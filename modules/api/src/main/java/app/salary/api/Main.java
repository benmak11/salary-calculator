package app.salary.api;

import app.salary.api.auth.AppleIdentityVerifier;
import app.salary.api.auth.AuthController;
import app.salary.api.controller.AccountController;
import app.salary.api.auth.AuthMiddleware;
import app.salary.api.auth.GoogleIdentityVerifier;
import app.salary.api.auth.SessionTokenService;
import app.salary.api.client.FinnhubStockClient;
import app.salary.api.client.GenerativeAiClient;
import app.salary.api.client.GoogleIdTokenSupplier;
import app.salary.api.client.HttpRulePackClient;
import app.salary.api.client.StockClient;
import app.salary.api.client.VertexGenerativeAiClient;
import app.salary.api.controller.BudgetController;
import app.salary.api.controller.BudgetPlanController;
import app.salary.api.controller.CalculateController;
import app.salary.api.controller.CalculationHistoryController;
import app.salary.api.controller.AccountLinkController;
import app.salary.api.controller.EventsController;
import app.salary.api.ratelimit.RateLimitMiddleware;
import app.salary.api.ratelimit.RateLimiter;
import app.salary.api.controller.GrantsController;
import app.salary.api.controller.StocksController;
import app.salary.api.service.BudgetPlanService;
import app.salary.api.service.EntitlementService;
import app.salary.api.service.SubscriptionRequiredException;
import app.salary.api.store.AccountDirectory;
import app.salary.api.store.BudgetStore;
import app.salary.api.store.CalculationStore;
import app.salary.api.store.EntitlementStore;
import app.salary.api.store.LinkCodeStore;
import app.salary.api.store.EventStore;
import app.salary.api.store.GrantStore;
import app.salary.api.store.StoreFactory;
import app.salary.api.store.UserDirectory;
import app.salary.api.validation.RequestValidator;
import app.salary.api.validation.ValidationException;
import app.salary.api.version.ClientVersion;
import app.salary.api.version.ClientVersionMiddleware;
import app.salary.api.version.UpgradeRequiredException;
import app.salary.calculator.client.RulePackClient;
import app.salary.calculator.countries.UKCalculator;
import app.salary.calculator.countries.USCalculator;
import app.salary.calculator.engine.CalculationOrchestrator;
import app.salary.calculator.engine.CountryCalculator;
import app.salary.calculator.registry.CalculatorRegistry;
import app.salary.calculator.shared.DeductionCalculator;
import app.salary.calculator.shared.StudentLoanCalculator;
import app.salary.calculator.shared.TaxBracketCalculator;
import app.salary.common.constants.ApiConstants;
import app.salary.rules.RulesRegistry;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import io.javalin.json.JavalinJackson;
import io.javalin.micrometer.MicrometerPlugin;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Entry point for the Salary Calculator API.
 *
 * Manually wires every dependency (no DI framework). All configuration is loaded from
 * environment variables — see {@code Env} below.
 */
public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final Logger access = LoggerFactory.getLogger("app.salary.api.access");

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String MDC_REQUEST_ID    = ApiConstants.MDC_REQUEST_ID;
    private static final String ATTR_START_NANOS  = "_start_nanos";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public static void main(String[] args) {
        int port = Env.intValue();
        String rulePackServiceUrl = Env.stringValue("RULE_PACK_SERVICE_URL", "");
        String rulePackAudience = Env.stringValue("RULE_PACK_AUDIENCE", "");
        String projectId = Env.stringValue("GCP_PROJECT_ID", "salary-calculator-dev");
        boolean enableGcp = Env.boolValue(detectGcpAvailable());
        String appleAudience = Env.stringValue("APPLE_AUDIENCE", "");
        String googleAudience = Env.stringValue("GOOGLE_AUDIENCE", "");
        String sessionSecretEnv = Env.stringValue("SESSION_JWT_SECRET", "");
        String finnhubApiKey = Env.stringValue("FINNHUB_API_KEY", "");
        String finnhubBaseUrl = Env.stringValue("FINNHUB_BASE_URL", "https://finnhub.io/api/v1");
        // gemini-3.1-flash-lite is a global-endpoint-only model on Vertex AI — it has
        // no regional binding, so location must be "global" (a regional value like
        // us-central1 returns a 404 "publisher model not found"). Model is env-tunable
        // so a future swap is a Cloud Run env change, not a code deploy.
        String vertexAiLocation = Env.stringValue("VERTEX_AI_LOCATION", "global");
        String vertexAiModel = Env.stringValue("VERTEX_AI_MODEL", "gemini-3.1-flash-lite");
        // A ceiling, not a delay: a call that answers in 3s still answers in 3s. 10s is
        // tight enough that a pathological call cannot hold a request slot for long, and
        // loose enough for a flash-lite budget-plan prompt. Cutting a slow-but-successful
        // call costs the user only the on-device fallback.
        //
        // Env-tunable so retuning from observed latency is a Cloud Run change, not a
        // redeploy — same reasoning as VERTEX_AI_MODEL/VERTEX_AI_LOCATION above. Must stay
        // well below Cloud Run's request timeout, or the platform kills the request before
        // this deadline fires and the client loses its graceful fallback.
        int vertexAiTimeoutSeconds = Env.intValue("VERTEX_AI_TIMEOUT_SECONDS", 10);

        // ── Shared infra ─────────────────────────────────────────────────────
        ObjectMapper objectMapper = buildObjectMapper();
        PrometheusMeterRegistry meterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        RequestValidator requestValidator = new RequestValidator();

        // ── Domain components (calculator graph) ─────────────────────────────
        TaxBracketCalculator bracketCalculator = new TaxBracketCalculator();
        DeductionCalculator deductionCalculator = new DeductionCalculator();
        StudentLoanCalculator studentLoanCalculator = new StudentLoanCalculator();

        List<CountryCalculator> calculators = List.of(
                new USCalculator(bracketCalculator, deductionCalculator),
                new UKCalculator(bracketCalculator, deductionCalculator, studentLoanCalculator)
        );

        CalculatorRegistry calculatorRegistry = new CalculatorRegistry(calculators);
        RulesRegistry rulesRegistry = new RulesRegistry();

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        Supplier<String> idTokenSupplier = rulePackAudience.isBlank()
                ? null
                : new GoogleIdTokenSupplier(rulePackAudience);
        RulePackClient rulePackClient = new HttpRulePackClient(
                httpClient, objectMapper, rulePackServiceUrl, idTokenSupplier);

        CalculationOrchestrator orchestrator = new CalculationOrchestrator(
                rulesRegistry, calculatorRegistry, rulePackClient, meterRegistry);

        StockClient stockClient = buildStockClient(httpClient, objectMapper, finnhubApiKey, finnhubBaseUrl);
        BudgetPlanService budgetPlanService = buildBudgetPlanService(
                projectId, vertexAiLocation, vertexAiModel, vertexAiTimeoutSeconds, enableGcp, objectMapper);

        // ── Persistence + identity (lazy / optional) ─────────────────────────
        Firestore firestore = enableGcp ? buildFirestore(projectId) : null;
        UserDirectory userDirectory = StoreFactory.userDirectory(firestore);
        AccountDirectory accountDirectory = StoreFactory.accountDirectory(firestore);
        CalculationStore calculationStore = StoreFactory.calculationStore(firestore, objectMapper);
        GrantStore grantStore = StoreFactory.grantStore(firestore, objectMapper);
        BudgetStore budgetStore = StoreFactory.budgetStore(firestore, objectMapper);
        EventStore eventStore = StoreFactory.eventStore(firestore);
        EntitlementStore entitlementStore = StoreFactory.entitlementStore(firestore);
        LinkCodeStore linkCodeStore = StoreFactory.linkCodeStore(firestore);
        if (firestore == null) {
            log.warn("Firestore unavailable (ENABLE_GCP={}); user directory + accounts + calculation history + grants + budget + events are in-memory only.", enableGcp);
        }

        SessionTokenService sessionTokens = buildSessionTokens(sessionSecretEnv);
        AuthMiddleware authMiddleware = new AuthMiddleware(sessionTokens);
        AuthController authController = buildAuthController(
                appleAudience, googleAudience, sessionSecretEnv, sessionTokens, userDirectory,
                accountDirectory, requestValidator);

        CalculationHistoryController historyController = new CalculationHistoryController(calculationStore);
        AccountController accountController =
                new AccountController(calculationStore, grantStore, budgetStore, userDirectory,
                        accountDirectory, entitlementStore, linkCodeStore);
        GrantsController grantsController = new GrantsController(grantStore, requestValidator);
        BudgetController budgetController = new BudgetController(budgetStore, requestValidator);
        EntitlementService entitlementService = buildEntitlementService(entitlementStore, accountDirectory);
        RateLimiter linkRedeemLimiter = buildLinkRedeemLimiter();
        AccountLinkController accountLinkController =
                new AccountLinkController(linkCodeStore, accountDirectory, entitlementService,
                        linkRedeemLimiter);
        BudgetPlanController budgetPlanController =
                new BudgetPlanController(budgetPlanService, requestValidator, entitlementService, accountDirectory);
        StocksController stocksController = new StocksController(stockClient);
        EventsController eventsController =
                new EventsController(eventStore, accountDirectory, requestValidator);
        RateLimitMiddleware rateLimitMiddleware = buildRateLimiter();
        ClientVersionMiddleware clientVersionMiddleware = buildClientVersionGate();

        Javalin app = createApp(objectMapper, meterRegistry, orchestrator,
                calculatorRegistry, requestValidator, calculationStore, rulesRegistry,
                authMiddleware, authController, historyController, accountController,
                grantsController, budgetController, budgetPlanController, stocksController,
                eventsController, rateLimitMiddleware, clientVersionMiddleware, accountLinkController);

        // ── Boot ─────────────────────────────────────────────────────────────
        app.start(port);
        log.info("Salary Calculator API listening on :{}", port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down Salary Calculator API");
            app.stop();
        }, "salary-calculator-shutdown"));
    }

    /**
     * Builds the Javalin app with all routes, exception handlers, and observability endpoints
     * wired in. Extracted so tests can boot the same app shape via {@code JavalinTest.test(...)}.
     */
    @SuppressWarnings("java:S107") // hand-wired DI: every collaborator is passed explicitly by design
    static Javalin createApp(ObjectMapper objectMapper,
                             PrometheusMeterRegistry meterRegistry,
                             CalculationOrchestrator orchestrator,
                             CalculatorRegistry calculatorRegistry,
                             RequestValidator requestValidator,
                             CalculationStore calculationStore,
                             RulesRegistry rulesRegistry,
                             AuthMiddleware authMiddleware,
                             AuthController authController,
                             CalculationHistoryController historyController,
                             AccountController accountController,
                             GrantsController grantsController,
                             BudgetController budgetController,
                             BudgetPlanController budgetPlanController,
                             StocksController stocksController,
                             EventsController eventsController,
                             RateLimitMiddleware rateLimitMiddleware,
                             ClientVersionMiddleware clientVersionMiddleware,
                             AccountLinkController accountLinkController) {
        return Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(objectMapper, false));
            config.startup.showJavalinBanner = false;
            config.concurrency.useVirtualThreads = true;
            config.registerPlugin(new MicrometerPlugin(micrometerCfg ->
                    micrometerCfg.registry = meterRegistry
            ));

            config.routes.before(Main::beginRequest);

            // Before auth and rate limiting so the client platform/version lands in the MDC
            // for every request, including ones those two turn away.
            config.routes.before(clientVersionMiddleware::handle);

            if (authMiddleware != null) {
                config.routes.before(authMiddleware::handle);
            }

            // After auth so a signed-in caller is keyed on their userId rather than sharing
            // an IP bucket with everyone behind the same NAT.
            if (rateLimitMiddleware != null) {
                config.routes.before(rateLimitMiddleware::handle);
            }

            config.routes.after(Main::endRequest);

            config.routes.exception(ValidationException.class, (e, ctx) -> {
                log.warn("Validation failed: {}", e.getErrors());
                ctx.status(HttpStatus.BAD_REQUEST).json(e.getErrors());
            });
            // 426 with a JSON body regardless of what the client sent in Accept — an old
            // build's upgrade prompt cannot depend on a header that build may not set.
            config.routes.exception(UpgradeRequiredException.class, (e, ctx) ->
                    ctx.status(HttpStatus.UPGRADE_REQUIRED).json(e.getBody()));
            // 402 is the contract both clients map to SubscriptionRequired(feature), so the
            // paywall can open on the surface that was refused rather than a generic page.
            config.routes.exception(SubscriptionRequiredException.class, (e, ctx) ->
                    ctx.status(HttpStatus.PAYMENT_REQUIRED).json(Map.of(
                            ApiConstants.ERROR, "subscription_required",
                            "feature", e.getFeature())));
            config.routes.exception(IllegalArgumentException.class, (e, ctx) -> {
                log.warn("Illegal argument: {}", e.getMessage());
                ctx.status(HttpStatus.UNPROCESSABLE_CONTENT)
                        .json(Map.of(ApiConstants.ERROR, String.valueOf(e.getMessage())));
            });
            config.routes.exception(Exception.class, (e, ctx) -> {
                log.error("Unexpected error", e);
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .json(Map.of(ApiConstants.ERROR, "Internal server error"));
            });

            new CalculateController(orchestrator, calculatorRegistry, requestValidator, calculationStore, rulesRegistry)
                    .register(config.routes);
            if (authController != null) {
                authController.register(config.routes);
            }
            historyController.register(config.routes);
            accountController.register(config.routes);
            accountLinkController.register(config.routes);
            grantsController.register(config.routes);
            budgetController.register(config.routes);
            budgetPlanController.register(config.routes);
            stocksController.register(config.routes);
            eventsController.register(config.routes);

            config.routes.get("/actuator/health", ctx -> ctx.json(Map.of("status", "UP")));
            config.routes.get("/actuator/prometheus", ctx ->
                    ctx.contentType("text/plain; version=0.0.4").result(meterRegistry.scrape()));
        });
    }

    /**
     * The client platform/version, appended to the access line only when the caller sent
     * {@code X-Incomatic-Client}. Lines for callers without it (probes, curl, the marketing
     * site) are unchanged.
     *
     * <p>The MDC alone is not enough: the plain pattern renders only {@code request_id}, and
     * the JSON encoder emits an explicit allowlist. Without this the version gate's observe
     * mode would record nothing, and observing is the only thing it does until a minimum is set.
     */
    private static String accessLogClientSuffix() {
        String platform = MDC.get(ApiConstants.MDC_CLIENT_PLATFORM);
        if (platform == null) {
            return "";
        }
        return " client=" + platform + "/" + MDC.get(ApiConstants.MDC_CLIENT_VERSION);
    }

    /** Correlation id, MDC, and the timer the access log reads. */
    private static void beginRequest(io.javalin.http.Context ctx) {
        String requestId = ctx.header(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_REQUEST_ID, requestId);
        MDC.put("method", ctx.method().name());
        MDC.put("path", ctx.path());
        ctx.attribute(ATTR_START_NANOS, System.nanoTime());
        ctx.attribute(MDC_REQUEST_ID, requestId);
        ctx.header(REQUEST_ID_HEADER, requestId);
    }

    /** One access line per request. Clears the MDC even if logging it throws. */
    private static void endRequest(io.javalin.http.Context ctx) {
        try {
            // Everything below exists only to produce that line, including the MDC values the
            // JSON encoder reads off it, so skip the lot when the logger is turned down.
            if (!access.isInfoEnabled()) {
                return;
            }
            Long startNanos = ctx.attribute(ATTR_START_NANOS);
            long durationMs = startNanos != null
                    ? (System.nanoTime() - startNanos) / 1_000_000L
                    : -1L;
            int status = ctx.status().getCode();
            MDC.put("status",      String.valueOf(status));
            MDC.put("duration_ms", String.valueOf(durationMs));
            access.info("{} {} -> {} ({}ms){}",
                    ctx.method(), ctx.path(), status, durationMs, accessLogClientSuffix());
        } finally {
            MDC.clear();
        }
    }

    private static Firestore buildFirestore(String projectId) {
        try {
            return FirestoreOptions.getDefaultInstance().toBuilder()
                    .setProjectId(projectId)
                    .build()
                    .getService();
        } catch (Exception e) {
            log.warn("Failed to construct Firestore client (projectId={}): {}", projectId, e.getMessage());
            return null;
        }
    }

    private static GenerativeAiClient buildGenerativeAiClient(String projectId, String location,
                                                              int timeoutSeconds) {
        try {
            return new VertexGenerativeAiClient(projectId, location, Duration.ofSeconds(timeoutSeconds));
        } catch (Exception e) {
            log.warn("Failed to construct Vertex AI client (projectId={}, location={}): {}",
                    projectId, location, e.getMessage());
            return null;
        }
    }

    private static StockClient buildStockClient(HttpClient httpClient, ObjectMapper objectMapper,
                                                 String apiKey, String baseUrl) {
        if (apiKey.isBlank()) {
            log.warn("FINNHUB_API_KEY not set — /v1/stocks endpoints will answer 503 (manual price entry only).");
            return null;
        }
        return new FinnhubStockClient(httpClient, objectMapper, baseUrl, apiKey);
    }

    private static BudgetPlanService buildBudgetPlanService(String projectId, String vertexAiLocation,
                                                              String vertexAiModel, int timeoutSeconds,
                                                              boolean enableGcp,
                                                              ObjectMapper objectMapper) {
        GenerativeAiClient generativeAiClient = enableGcp
                ? buildGenerativeAiClient(projectId, vertexAiLocation, timeoutSeconds)
                : null;
        if (generativeAiClient == null) {
            log.warn("Vertex AI unavailable (ENABLE_GCP={}) — POST /v1/budget/plan will answer 503 "
                    + "(client falls back to its own on-device plan).", enableGcp);
            return null;
        }
        log.info("Vertex AI budget planning enabled: model={}, timeout={}s", vertexAiModel, timeoutSeconds);
        return new BudgetPlanService(generativeAiClient, objectMapper, vertexAiModel);
    }



    /**
     * Defaults are deliberately generous — this exists to stop one caller saturating an
     * instance at peak, not to police normal use. Unset env vars keep the defaults, which
     * avoids touching deploy.yml and the trap where {@code --set-env-vars} replaces the
     * whole set.
     */
    private static RateLimitMiddleware buildRateLimiter() {
        if (!Env.flag("RATE_LIMIT_ENABLED", true)) {
            log.warn("Rate limiting disabled (RATE_LIMIT_ENABLED=false).");
            return null;
        }
        int perMinute = Env.intValue("RATE_LIMIT_PER_MINUTE", 300);
        int eventsPerMinute = Env.intValue("EVENTS_RATE_LIMIT_PER_MINUTE", 60);
        int maxCallers = Env.intValue("RATE_LIMIT_MAX_CALLERS", 50_000);
        log.info("Rate limiting enabled: {}/min default, {}/min on /v1/events, tracking up to {} callers",
                perMinute, eventsPerMinute, maxCallers);
        return new RateLimitMiddleware(
                new RateLimiter(perMinute, Math.max(1, perMinute / 3), maxCallers),
                new RateLimiter(eventsPerMinute, Math.max(1, eventsPerMinute / 3), maxCallers));
    }

    /**
     * Deliberately mean. Ten a minute is generous for a human typing a code off another screen
     * and hostile to anyone enumerating a 10^6 space inside a 10-minute TTL. Applied by
     * {@code AccountLinkController} rather than the blanket middleware.
     */
    private static RateLimiter buildLinkRedeemLimiter() {
        if (!Env.flag("RATE_LIMIT_ENABLED", true)) {
            return null;
        }
        int perMinute = Env.intValue("LINK_REDEEM_RATE_LIMIT_PER_MINUTE", 10);
        int maxCallers = Env.intValue("RATE_LIMIT_MAX_CALLERS", 50_000);
        log.info("Link redemption limited to {}/min per caller", perMinute);
        return new RateLimiter(perMinute, Math.max(1, perMinute / 2), maxCallers);
    }

    /**
     * Both kill switches default to <b>off</b>, per the roadmap: the schema and the
     * resolution go live and get observed well before anyone is refused.
     *
     * <p>{@code PLAY_ENFORCEMENT} is separate from {@code SUBSCRIPTION_ENFORCEMENT} because
     * the two stores go live at different times, and a half-built Play integration writing
     * speculative records must not be able to grant Pro on its own.
     */
    private static EntitlementService buildEntitlementService(EntitlementStore entitlements,
                                                              AccountDirectory accounts) {
        boolean subscriptionEnforcement = Env.flag("SUBSCRIPTION_ENFORCEMENT", false);
        boolean playEnforcement = Env.flag("PLAY_ENFORCEMENT", false);
        log.info("Entitlements: subscription enforcement={}, play enforcement={}",
                subscriptionEnforcement, playEnforcement);
        return new EntitlementService(entitlements, accounts, subscriptionEnforcement, playEnforcement);
    }

    /**
     * Ships with no minimums, so the gate observes and blocks nothing. That is deliberate:
     * it must be live here and in a released iOS and Android build before any minimum is set,
     * because a gate can only act on clients that already send the header.
     *
     * <p>Flipping enforcement later means editing {@code deploy.yml} — {@code --set-env-vars}
     * replaces the whole set, so setting these in the Cloud Run console is wiped on the next
     * deploy.
     */
    private static ClientVersionMiddleware buildClientVersionGate() {
        Map<String, ClientVersion> minimums = new LinkedHashMap<>();
        addMinimum(minimums, ClientVersion.IOS, Env.stringValue("MIN_CLIENT_VERSION_IOS", ""));
        addMinimum(minimums, ClientVersion.ANDROID, Env.stringValue("MIN_CLIENT_VERSION_ANDROID", ""));
        if (minimums.isEmpty()) {
            log.info("Client version gate in observe mode: {} is recorded on every request, "
                    + "no minimum enforced.", ClientVersionMiddleware.CLIENT_HEADER);
        } else {
            log.info("Client version gate enforcing minimums: {}", minimums.values());
        }
        return new ClientVersionMiddleware(minimums);
    }

    /** An unparseable minimum leaves that platform unenforced rather than blocking every build. */
    private static void addMinimum(Map<String, ClientVersion> minimums, String platform, String configured) {
        if (configured.isBlank()) {
            return;
        }
        ClientVersion.parse(platform, configured).ifPresentOrElse(
                version -> minimums.put(platform, version),
                () -> log.warn("Minimum client version for {} is not a version ('{}'); "
                        + "leaving {} unenforced.", platform, configured, platform));
    }

    private static AuthController buildAuthController(String appleAudience, String googleAudience,
                                                        String sessionSecretEnv,
                                                        SessionTokenService sessionTokens,
                                                        UserDirectory userDirectory,
                                                        AccountDirectory accountDirectory,
                                                        RequestValidator requestValidator) {
        AppleIdentityVerifier appleVerifier = null;
        if (appleAudience.isBlank()) {
            log.warn("Sign in with Apple disabled (APPLE_AUDIENCE=<unset>, SESSION_JWT_SECRET configured: {})",
                    !sessionSecretEnv.isBlank());
        } else {
            appleVerifier = new AppleIdentityVerifier(appleAudience);
        }

        GoogleIdentityVerifier googleVerifier = null;
        if (googleAudience.isBlank()) {
            log.warn("Sign in with Google disabled (GOOGLE_AUDIENCE=<unset>, SESSION_JWT_SECRET configured: {})",
                    !sessionSecretEnv.isBlank());
        } else {
            googleVerifier = new GoogleIdentityVerifier(googleAudience);
        }

        return new AuthController(appleVerifier, googleVerifier, sessionTokens, userDirectory,
                accountDirectory, requestValidator);
    }

    private static SessionTokenService buildSessionTokens(String secretEnv) {
        byte[] secret;
        if (secretEnv.isBlank()) {
            secret = new byte[32];
            SECURE_RANDOM.nextBytes(secret);
            log.warn("SESSION_JWT_SECRET not set — generated an ephemeral 32-byte secret. "
                    + "All sessions will be invalidated on restart. Set SESSION_JWT_SECRET in production.");
            return new SessionTokenService(secret);
        }
        try {
            secret = Base64.getDecoder().decode(secretEnv);
        } catch (IllegalArgumentException e) {
            // Treat the env var as raw UTF-8 bytes when it isn't valid base64.
            secret = secretEnv.getBytes(StandardCharsets.UTF_8);
        }
        if (secret.length < 32) {
            throw new IllegalStateException("SESSION_JWT_SECRET must decode to at least 32 bytes (got "
                    + secret.length + ")");
        }
        return new SessionTokenService(secret);
    }

    private static boolean detectGcpAvailable() {
        return !Env.stringValue("GOOGLE_APPLICATION_CREDENTIALS", "").isEmpty()
                || !Env.stringValue("GOOGLE_CLOUD_PROJECT", "").isEmpty();
    }

    private static ObjectMapper buildObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.setDefaultPropertyInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
        return mapper;
    }

    /** Tiny env-var helper so we don't drag in spring-core just for {@code @Value}. */
    private static final class Env {
        static String stringValue(String key, String defaultValue) {
            String v = System.getenv(key);
            return (v == null || v.isBlank()) ? defaultValue : v;
        }

        static int intValue() {
            String v = System.getenv("SERVER_PORT");
            if (v == null || v.isBlank()) return 8080;
            try {
                return Integer.parseInt(v.trim());
            } catch (NumberFormatException nfe) {
                return 8080;
            }
        }

        static boolean boolValue(boolean defaultValue) {
            String v = System.getenv("ENABLE_GCP");
            if (v == null || v.isBlank()) return defaultValue;
            return "true".equalsIgnoreCase(v.trim()) || "1".equals(v.trim());
        }

        /** Falls back rather than failing: a typo in a tuning knob must not stop the service booting. */
        static int intValue(String key, int defaultValue) {
            String v = System.getenv(key);
            if (v == null || v.isBlank()) return defaultValue;
            try {
                int parsed = Integer.parseInt(v.trim());
                return parsed > 0 ? parsed : defaultValue;
            } catch (NumberFormatException nfe) {
                log.warn("{} is not a positive integer; using {}", key, defaultValue);
                return defaultValue;
            }
        }

        static boolean flag(String key, boolean defaultValue) {
            String v = System.getenv(key);
            if (v == null || v.isBlank()) return defaultValue;
            return "true".equalsIgnoreCase(v.trim()) || "1".equals(v.trim());
        }
    }
}
