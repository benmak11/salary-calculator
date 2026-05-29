package app.salary.rulepack;

import app.salary.rulepack.cache.RulePackCache;
import app.salary.rulepack.controller.RulePackController;
import app.salary.rulepack.event.RulePackLifecyclePublisher;
import app.salary.rulepack.graphql.RulePackGraphQLController;
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
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

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

        // ── Javalin ──────────────────────────────────────────────────────────
        Javalin app = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(objectMapper, false));
            config.showJavalinBanner = false;
            config.useVirtualThreads = true;
            config.bundledPlugins.enableCors(cors -> cors.addRule(it -> it.anyHost()));
            config.registerPlugin(new MicrometerPlugin(micrometerCfg -> {
                micrometerCfg.registry = meterRegistry;
            }));
        });

        app.exception(IllegalArgumentException.class, (e, ctx) -> {
            log.warn("Illegal argument: {}", e.getMessage());
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", String.valueOf(e.getMessage())));
        });
        app.exception(Exception.class, (e, ctx) -> {
            log.error("Unexpected error", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("error", "Internal server error"));
        });

        if (rulePackService != null) {
            new RulePackController(rulePackService).register(app);
            new RulePackGraphQLController(rulePackService, objectMapper).register(app);
        } else {
            // Stub the persistence-backed routes with 503 — the rest of the service
            // (health, metrics) still boots cleanly so docker-compose stays happy.
            io.javalin.http.Handler unavailable = ctx -> ctx.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .json(Map.of("error", "GCP backends are disabled (ENABLE_GCP=false)"));
            app.get(  "/v1/rule-packs",                unavailable);
            app.get(  "/v1/rule-packs/latest",         unavailable);
            app.get(  "/v1/rule-packs/{id}",           unavailable);
            app.get(  "/v1/rule-packs/{id}/download",  unavailable);
            app.post( "/v1/rule-packs",                unavailable);
            app.post( "/v1/rule-packs/{id}/publish",   unavailable);
            app.post( "/v1/rule-packs/{id}/deprecate", unavailable);
            app.post( "/graphql",                      unavailable);
        }

        app.get("/actuator/health", ctx -> ctx.json(Map.of(
                "status", "UP",
                "gcp", enableGcp
        )));
        app.get("/actuator/prometheus", ctx ->
                ctx.contentType("text/plain; version=0.0.4").result(meterRegistry.scrape()));

        app.start(port);
        log.info("Rule Pack Service listening on :{} (GCP enabled: {})", port, enableGcp);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down Rule Pack Service");
            try { app.stop(); } catch (Exception e) { log.warn("Error stopping Javalin", e); }
            lifecyclePublisher.shutdown();
        }, "rule-pack-shutdown"));
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
        return Env.stringValue("GOOGLE_APPLICATION_CREDENTIALS", "").length() > 0
                || Env.stringValue("GOOGLE_CLOUD_PROJECT", "").length() > 0;
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
