package app.salary.api.version;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientVersionMiddlewareTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Enforcement off, which is how this ships. */
    private static final Map<String, ClientVersion> OBSERVE_ONLY = Map.of();

    private static final Map<String, ClientVersion> ENFORCED = Map.of(
            ClientVersion.IOS, new ClientVersion(ClientVersion.IOS, 1, 9, 0),
            ClientVersion.ANDROID, new ClientVersion(ClientVersion.ANDROID, 1, 0, 0));

    private static Javalin app(Map<String, ClientVersion> minimums) {
        ClientVersionMiddleware middleware = new ClientVersionMiddleware(minimums);
        return Javalin.create(config -> {
            config.startup.showJavalinBanner = false;
            config.routes.before(middleware::handle);
            // Mirrors the handler Main registers; the 426 body is rendered there, not in the
            // middleware, so a test without it would prove nothing about the real response.
            config.routes.exception(UpgradeRequiredException.class, (e, ctx) ->
                    ctx.status(HttpStatus.UPGRADE_REQUIRED).json(e.getBody()));
            config.routes.post("/v1/calculate", ctx -> ctx.json(Map.of("ok", true)));
            config.routes.post("/v1/events", ctx -> ctx.json(Map.of("accepted", 1)));
            config.routes.get("/actuator/health", ctx -> ctx.json(Map.of("status", "UP")));
        });
    }

    private static Consumer<io.javalin.testtools.Request.Builder> client(String header) {
        return r -> r.header(ClientVersionMiddleware.CLIENT_HEADER, header);
    }

    @Test
    void blocksNothingWhenNoMinimumIsConfigured() {
        // The shipping default. The gate has to be in the field before it can enforce anything,
        // so an ancient build must sail through until a minimum is set.
        JavalinTest.test(app(OBSERVE_ONLY), (server, http) ->
                assertEquals(200, http.post("/v1/calculate", null, client("ios/0.1.0")).code()));
    }

    @Test
    void rejectsAClientBelowTheMinimumWithAStructuredBody() {
        JavalinTest.test(app(ENFORCED), (server, http) -> {
            var response = http.post("/v1/calculate", null, client("ios/1.8.9"));
            assertEquals(426, response.code());

            JsonNode body = MAPPER.readTree(response.body().string());
            // `error` is the stable contract the client switches on.
            assertEquals("upgrade_required", body.get("error").asText());
            assertEquals("ios", body.get("platform").asText());
            assertEquals("1.8.9", body.get("currentVersion").asText());
            assertEquals("1.9.0", body.get("minimumVersion").asText());
        });
    }

    @Test
    void admitsTheMinimumVersionItself() {
        JavalinTest.test(app(ENFORCED), (server, http) -> {
            assertEquals(200, http.post("/v1/calculate", null, client("ios/1.9.0")).code());
            assertEquals(200, http.post("/v1/calculate", null, client("ios/2.0.0")).code());
        });
    }

    @Test
    void enforcesEachPlatformAgainstItsOwnMinimum() {
        // android/1.0.0 is current; the same numbers on iOS are ancient.
        JavalinTest.test(app(ENFORCED), (server, http) -> {
            assertEquals(200, http.post("/v1/calculate", null, client("android/1.0.0")).code());
            assertEquals(426, http.post("/v1/calculate", null, client("ios/1.0.0")).code());
        });
    }

    @Test
    void ignoresPlatformsWithNoConfiguredMinimum() {
        Map<String, ClientVersion> iosOnly = Map.of(
                ClientVersion.IOS, new ClientVersion(ClientVersion.IOS, 1, 9, 0));
        JavalinTest.test(app(iosOnly), (server, http) ->
                assertEquals(200, http.post("/v1/calculate", null, client("android/0.0.1")).code()));
    }

    @Test
    void neverBlocksAnUnknownClient() {
        // curl, the marketing site, and anything whose header we got wrong.
        JavalinTest.test(app(ENFORCED), (server, http) -> {
            assertEquals(200, http.post("/v1/calculate", null).code());
            assertEquals(200, http.post("/v1/calculate", null, client("garbage")).code());
            assertEquals(200, http.post("/v1/calculate", null, client("")).code());
        });
    }

    @Test
    void letsAnOldClientKeepSendingAnalytics() {
        // Blocking these would erase the measurement of how many old clients remain, which is
        // the number that says whether raising the minimum is safe.
        JavalinTest.test(app(ENFORCED), (server, http) ->
                assertEquals(200, http.post("/v1/events", null, client("ios/1.0.0")).code()));
    }

    @Test
    void leavesHealthChecksAlone() {
        JavalinTest.test(app(ENFORCED), (server, http) ->
                assertEquals(200, http.get("/actuator/health", client("ios/1.0.0")).code()));
    }

    @Test
    void treatsANullMinimumMapAsObserveOnly() {
        JavalinTest.test(app(null), (server, http) ->
                assertEquals(200, http.post("/v1/calculate", null, client("ios/0.0.1")).code()));
    }
}
