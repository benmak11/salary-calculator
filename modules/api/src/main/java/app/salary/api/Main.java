package app.salary.api;

import app.salary.api.auth.AppleIdentityVerifier;
import app.salary.api.auth.AuthController;
import app.salary.api.auth.AuthMiddleware;
import app.salary.api.auth.SessionTokenService;
import app.salary.api.client.GoogleIdTokenSupplier;
import app.salary.api.client.HttpRulePackClient;
import app.salary.api.controller.CalculateController;
import app.salary.api.controller.CalculationHistoryController;
import app.salary.api.store.CalculationStore;
import app.salary.api.store.FirestoreCalculationStore;
import app.salary.api.store.FirestoreUserDirectory;
import app.salary.api.store.InMemoryCalculationStore;
import app.salary.api.store.InMemoryUserDirectory;
import app.salary.api.store.UserDirectory;
import app.salary.api.validation.RequestValidator;
import app.salary.api.validation.ValidationException;
import app.salary.calculator.client.RulePackClient;
import app.salary.calculator.countries.UKCalculator;
import app.salary.calculator.countries.USCalculator;
import app.salary.calculator.engine.CalculationOrchestrator;
import app.salary.calculator.engine.CountryCalculator;
import app.salary.calculator.registry.CalculatorRegistry;
import app.salary.calculator.shared.DeductionCalculator;
import app.salary.calculator.shared.IncomeCalculator;
import app.salary.calculator.shared.StudentLoanCalculator;
import app.salary.calculator.shared.TaxBracketCalculator;
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
    private static final String MDC_REQUEST_ID    = "request_id";
    private static final String ATTR_START_NANOS  = "_start_nanos";

    public static void main(String[] args) {
        int port = Env.intValue("SERVER_PORT", 8080);
        String rulePackServiceUrl = Env.stringValue("RULE_PACK_SERVICE_URL", "");
        String rulePackAudience = Env.stringValue("RULE_PACK_AUDIENCE", "");
        String projectId = Env.stringValue("GCP_PROJECT_ID", "salary-calculator-dev");
        boolean enableGcp = Env.boolValue("ENABLE_GCP", detectGcpAvailable());
        String appleAudience = Env.stringValue("APPLE_AUDIENCE", "");
        String sessionSecretEnv = Env.stringValue("SESSION_JWT_SECRET", "");

        // ── Shared infra ─────────────────────────────────────────────────────
        ObjectMapper objectMapper = buildObjectMapper();
        PrometheusMeterRegistry meterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        RequestValidator requestValidator = new RequestValidator();

        // ── Domain components (calculator graph) ─────────────────────────────
        TaxBracketCalculator bracketCalculator = new TaxBracketCalculator();
        DeductionCalculator deductionCalculator = new DeductionCalculator();
        StudentLoanCalculator studentLoanCalculator = new StudentLoanCalculator();
        IncomeCalculator incomeCalculator = new IncomeCalculator();
        log.debug("IncomeCalculator constructed (reserved for future request normalization): {}", incomeCalculator);

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

        // ── Persistence + identity (lazy / optional) ─────────────────────────
        Firestore firestore = enableGcp ? buildFirestore(projectId) : null;
        UserDirectory userDirectory = firestore != null
                ? new FirestoreUserDirectory(firestore)
                : new InMemoryUserDirectory();
        CalculationStore calculationStore = firestore != null
                ? new FirestoreCalculationStore(firestore, objectMapper)
                : new InMemoryCalculationStore();
        if (firestore == null) {
            log.warn("Firestore unavailable (ENABLE_GCP={}); user directory + calculation history are in-memory only.", enableGcp);
        }

        AppleIdentityVerifier appleVerifier = appleAudience.isBlank()
                ? null
                : new AppleIdentityVerifier(appleAudience);
        SessionTokenService sessionTokens = buildSessionTokens(sessionSecretEnv);
        AuthMiddleware authMiddleware = sessionTokens != null ? new AuthMiddleware(sessionTokens) : null;
        AuthController authController = (appleVerifier != null && sessionTokens != null)
                ? new AuthController(appleVerifier, sessionTokens, userDirectory, requestValidator)
                : null;
        if (authController == null) {
            log.warn("Sign in with Apple disabled (APPLE_AUDIENCE={}, SESSION_JWT_SECRET configured: {})",
                    appleAudience.isBlank() ? "<unset>" : "set",
                    !sessionSecretEnv.isBlank());
        }

        CalculationHistoryController historyController = new CalculationHistoryController(calculationStore);

        Javalin app = createApp(objectMapper, meterRegistry, orchestrator,
                calculatorRegistry, requestValidator, calculationStore,
                authMiddleware, authController, historyController);

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
    static Javalin createApp(ObjectMapper objectMapper,
                             PrometheusMeterRegistry meterRegistry,
                             CalculationOrchestrator orchestrator,
                             CalculatorRegistry calculatorRegistry,
                             RequestValidator requestValidator,
                             CalculationStore calculationStore,
                             AuthMiddleware authMiddleware,
                             AuthController authController,
                             CalculationHistoryController historyController) {
        return Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(objectMapper, false));
            config.startup.showJavalinBanner = false;
            config.concurrency.useVirtualThreads = true;
            config.registerPlugin(new MicrometerPlugin(micrometerCfg -> {
                micrometerCfg.registry = meterRegistry;
            }));

            config.routes.before(ctx -> {
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
            });

            if (authMiddleware != null) {
                config.routes.before(authMiddleware::handle);
            }

            config.routes.after(ctx -> {
                try {
                    Long startNanos = ctx.attribute(ATTR_START_NANOS);
                    long durationMs = startNanos != null
                            ? (System.nanoTime() - startNanos) / 1_000_000L
                            : -1L;
                    int status = ctx.status().getCode();
                    MDC.put("status",      String.valueOf(status));
                    MDC.put("duration_ms", String.valueOf(durationMs));
                    access.info("{} {} -> {} ({}ms)",
                            ctx.method(), ctx.path(), status, durationMs);
                } finally {
                    MDC.clear();
                }
            });

            config.routes.exception(ValidationException.class, (e, ctx) -> {
                log.warn("Validation failed: {}", e.getErrors());
                ctx.status(HttpStatus.BAD_REQUEST).json(e.getErrors());
            });
            config.routes.exception(IllegalArgumentException.class, (e, ctx) -> {
                log.warn("Illegal argument: {}", e.getMessage());
                ctx.status(HttpStatus.UNPROCESSABLE_CONTENT)
                        .json(Map.of("error", String.valueOf(e.getMessage())));
            });
            config.routes.exception(Exception.class, (e, ctx) -> {
                log.error("Unexpected error", e);
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .json(Map.of("error", "Internal server error"));
            });

            new CalculateController(orchestrator, calculatorRegistry, requestValidator, calculationStore)
                    .register(config.routes);
            if (authController != null) {
                authController.register(config.routes);
            }
            historyController.register(config.routes);

            config.routes.get("/actuator/health", ctx -> ctx.json(Map.of("status", "UP")));
            config.routes.get("/actuator/prometheus", ctx ->
                    ctx.contentType("text/plain; version=0.0.4").result(meterRegistry.scrape()));
        });
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

    private static SessionTokenService buildSessionTokens(String secretEnv) {
        byte[] secret;
        if (secretEnv.isBlank()) {
            secret = new byte[32];
            new SecureRandom().nextBytes(secret);
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
        return Env.stringValue("GOOGLE_APPLICATION_CREDENTIALS", "").length() > 0
                || Env.stringValue("GOOGLE_CLOUD_PROJECT", "").length() > 0;
    }

    private static ObjectMapper buildObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
        return mapper;
    }

    /** Tiny env-var helper so we don't drag in spring-core just for {@code @Value}. */
    private static final class Env {
        static String stringValue(String key, String defaultValue) {
            String v = System.getenv(key);
            return (v == null || v.isBlank()) ? defaultValue : v;
        }

        static int intValue(String key, int defaultValue) {
            String v = System.getenv(key);
            if (v == null || v.isBlank()) return defaultValue;
            try {
                return Integer.parseInt(v.trim());
            } catch (NumberFormatException nfe) {
                return defaultValue;
            }
        }

        static boolean boolValue(String key, boolean defaultValue) {
            String v = System.getenv(key);
            if (v == null || v.isBlank()) return defaultValue;
            return "true".equalsIgnoreCase(v.trim()) || "1".equals(v.trim());
        }
    }
}
