package app.salary.rulepack;

import app.salary.rulepack.service.RulePackService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Smoke tests for the rule-pack-service {@link Main} app wiring under Javalin 7.
 *
 * Both wiring paths are exercised: with a real {@link RulePackService} (full controller
 * registration) and with {@code null} (stub-503 fallback for the {@code ENABLE_GCP=false}
 * docker-compose default).
 */
class MainTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private Javalin appWithService() {
        return Main.createApp(MAPPER,
                new PrometheusMeterRegistry(PrometheusConfig.DEFAULT),
                mock(RulePackService.class),
                true);
    }

    private Javalin appWithoutService() {
        return Main.createApp(MAPPER,
                new PrometheusMeterRegistry(PrometheusConfig.DEFAULT),
                null,
                false);
    }

    @Test
    void actuatorHealth_reportsGcpFlag() {
        JavalinTest.test(appWithService(), (server, client) -> {
            var response = client.get("/actuator/health");
            assertEquals(200, response.code());
            JsonNode body = MAPPER.readTree(response.body().string());
            assertEquals("UP", body.get("status").asText());
            assertTrue(body.get("gcp").asBoolean());
        });
    }

    @Test
    void actuatorHealth_whenGcpDisabled_reportsFalse() {
        JavalinTest.test(appWithoutService(), (server, client) -> {
            var response = client.get("/actuator/health");
            assertEquals(200, response.code());
            JsonNode body = MAPPER.readTree(response.body().string());
            assertFalse(body.get("gcp").asBoolean());
        });
    }

    @Test
    void actuatorPrometheus_servesScrapeFormat() {
        JavalinTest.test(appWithService(), (server, client) -> {
            var response = client.get("/actuator/prometheus");
            assertEquals(200, response.code());
            assertNotNull(response.body().string());
        });
    }

    @Test
    void rulePackRoutes_areRegistered_whenServicePresent() {
        JavalinTest.test(appWithService(), (server, client) -> {
            // findRulePacks isn't stubbed; service.findRulePacks returns a default mock
            // (null) which makes the controller blow up — we only need to confirm a
            // non-404 response (i.e. the route is mounted).
            var response = client.get("/v1/rule-packs");
            assertTrue(response.code() != 404,
                    "Expected /v1/rule-packs to be registered, got " + response.code());
        });
    }

    @Test
    void rulePackRoutes_return503_whenServiceNull() {
        JavalinTest.test(appWithoutService(), (server, client) -> {
            var response = client.get("/v1/rule-packs");
            assertEquals(503, response.code());

            JsonNode body = MAPPER.readTree(response.body().string());
            assertTrue(body.get("error").asText().contains("GCP backends are disabled"));
        });
    }

}
