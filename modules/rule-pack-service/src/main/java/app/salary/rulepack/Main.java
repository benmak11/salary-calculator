package app.salary.rulepack;

import app.salary.rulepack.cache.RulePackCache;
import app.salary.rulepack.constants.ApiConstants;
import app.salary.rulepack.controller.RulePackController;
import app.salary.rulepack.event.RulePackLifecyclePublisher;
import app.salary.rulepack.repository.RulePackRepository;
import app.salary.rulepack.service.RulePackService;
import app.salary.rulepack.service.RulePackStorageService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.pubsub.v1.TopicName;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import io.javalin.json.JavalinJackson;
import io.javalin.micrometer.MicrometerPlugin;
import io.javalin.plugin.bundled.CorsPluginConfig;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Map;
import java.util.UUID;

/**
 * Entry point for the Rule Pack Service.
 *
 * Manually wires Firestore, GCS, Pub/Sub, and Caffeine cache, then registers
 * Javalin routes for the REST + GraphQL endpoints. GCP clients are constructed
 * lazily so the service can boot without Application Default Credentials when
 * running locally (calls to Firestore/GCS will surface their real errors, but
 * boot itself does not require GCP).
 */
public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final Logger access = LoggerFactory.getLogger("app.salary.rulepack.access");

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String MDC_REQUEST_ID    = "request_id";
    private static final String ATTR_START_NANOS  = "_start_nanos";

    public static void main(String[] args) {
        int port           = Env.intValue(   "SERVER_PORT",          8081);
        String projectId   = Env.stringValue("GCP_PROJECT_ID",       "salary-calculator-dev");
        String bucketName  = Env.stringValue("GCP_BUCKET_RULEPACKS", "salary-calculator-rulepacks");
        String pubsubTopic = Env.stringValue("PUBSUB_TOPIC",         "rule-pack-lifecycle");
        boolean enableGcp  = Env.boolValue(  "ENABLE_GCP",           detectGcpAvailable());

        ObjectMapper objectMapper = buildObjectMapper();
        PrometheusMeterRegistry meterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

        // ── GCP clients (best-effort — null when ENABLE_GCP=false or creds missing) ──
        Firestore firestore = enableGcp ? buildFirestore(projectId) : null;
        Storage storage     = enableGcp ? buildStorage(projectId)   : null;
        Publisher publisher = enableGcp ? buildPublisher(projectId, pubsubTopic) : null;

        if (!enableGcp) {
            log.warn("ENABLE_GCP=false — Firestore/GCS/Pub/Sub are NOT wired. REST and GraphQL calls "
                    + "that touch persistence will fail. Set ENABLE_GCP=true and provide ADC to enable.");
        }

        // ── Domain layer ─────────────────────────────────────────────────────
        RulePackRepository repository = firestore != null ? new RulePackRepository(firestore) : null;
        RulePackStorageService storageService = storage != null
                ? new RulePackStorageService(storage, bucketName, objectMapper)
                : null;
        RulePackLifecyclePublisher lifecyclePublisher =
                new RulePackLifecyclePublisher(publisher, objectMapper, pubsubTopic);
        RulePackCache cache = new RulePackCache();

        RulePackService rulePackService = (repository != null && storageService != null)
                ? new RulePackService(repository, storageService, lifecyclePublisher, cache)
                : null;

        Javalin app = createApp(objectMapper, meterRegistry, rulePackService, enableGcp);

        app.start(port);
        log.info("Rule Pack Service listening on :{} (GCP enabled: {})", port, enableGcp);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down Rule Pack Service");
            try { app.stop(); } catch (Exception e) { log.warn("Error stopping Javalin", e); }
            lifecyclePublisher.shutdown();
        }, "rule-pack-shutdown"));
    }

    /**
     * Builds the Javalin app. Extracted so tests can boot the same app shape via
     * {@code JavalinTest.test(...)} with a stubbed {@link RulePackService}.
     *
     * Javalin 7: routes and exception handlers must be registered inside the config block
     * via {@code config.routes.xxx}; the Javalin instance no longer exposes
     * {@code get/post/exception} after construction.
     */
    static Javalin createApp(ObjectMapper objectMapper,
                             PrometheusMeterRegistry meterRegistry,
                             RulePackService rulePackService,
                             boolean enableGcp) {
        return Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(objectMapper, false));
            config.startup.showJavalinBanner = false;
            config.concurrency.useVirtualThreads = true;
            config.bundledPlugins.enableCors(cors -> cors.addRule(CorsPluginConfig.CorsRule::anyHost));
            config.registerPlugin(new MicrometerPlugin(micrometerCfg -> micrometerCfg.registry = meterRegistry));

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

            config.routes.exception(IllegalArgumentException.class, (e, ctx) -> {
                log.warn("Illegal argument: {}", e.getMessage());
                ctx.status(HttpStatus.BAD_REQUEST).json(Map.of(ApiConstants.ERROR, String.valueOf(e.getMessage())));
            });
            config.routes.exception(Exception.class, (e, ctx) -> {
                log.error("Unexpected error", e);
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of(ApiConstants.ERROR, "Internal server error"));
            });

            if (rulePackService != null) {
                new RulePackController(rulePackService).register(config.routes);
            } else {
                io.javalin.http.Handler unavailable = ctx -> ctx.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .json(Map.of(ApiConstants.ERROR, "GCP backends are disabled (ENABLE_GCP=false)"));
                config.routes.get(  "/v1/rule-packs",                unavailable);
                config.routes.get(  "/v1/rule-packs/latest",         unavailable);
                config.routes.get(  "/v1/rule-packs/{id}",           unavailable);
                config.routes.get(  "/v1/rule-packs/{id}/download",  unavailable);
                config.routes.post( "/v1/rule-packs",                unavailable);
                config.routes.post( "/v1/rule-packs/{id}/publish",   unavailable);
                config.routes.post( "/v1/rule-packs/{id}/deprecate", unavailable);
            }

            config.routes.get("/actuator/health", ctx -> ctx.json(Map.of(
                    "status", "UP",
                    "gcp", enableGcp
            )));
            config.routes.get("/actuator/prometheus", ctx ->
                    ctx.contentType("text/plain; version=0.0.4").result(meterRegistry.scrape()));
        });
    }

    // ── client builders ──────────────────────────────────────────────────────

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

    private static Storage buildStorage(String projectId) {
        try {
            return StorageOptions.newBuilder()
                    .setProjectId(projectId)
                    .build()
                    .getService();
        } catch (Exception e) {
            log.warn("Failed to construct GCS client (projectId={}): {}", projectId, e.getMessage());
            return null;
        }
    }

    private static Publisher buildPublisher(String projectId, String topic) {
        try {
            TopicName topicName = TopicName.of(projectId, topic);
            return Publisher.newBuilder(topicName).build();
        } catch (Exception e) {
            log.warn("Failed to construct Pub/Sub publisher (topic={}): {}", topic, e.getMessage());
            return null;
        }
    }

    private static boolean detectGcpAvailable() {
        // Default to "enabled" only when an explicit credentials env var is present.
        return !Env.stringValue("GOOGLE_APPLICATION_CREDENTIALS", "").isEmpty()
                || !Env.stringValue("GOOGLE_CLOUD_PROJECT", "").isEmpty();
    }

    private static ObjectMapper buildObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }

    private static final class Env {
        static String stringValue(String key, String defaultValue) {
            String v = System.getenv(key);
            return (v == null || v.isBlank()) ? defaultValue : v;
        }

        static int intValue(String key, int defaultValue) {
            String v = System.getenv(key);
            if (v == null || v.isBlank()) return defaultValue;
            try { return Integer.parseInt(v.trim()); }
            catch (NumberFormatException nfe) { return defaultValue; }
        }

        static boolean boolValue(String key, boolean defaultValue) {
            String v = System.getenv(key);
            if (v == null || v.isBlank()) return defaultValue;
            return "true".equalsIgnoreCase(v.trim()) || "1".equals(v.trim());
        }
    }
}
