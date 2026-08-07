package app.salary.api.ratelimit;

import app.salary.api.auth.AuthMiddleware;
import app.salary.api.auth.SessionTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RateLimitMiddlewareTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SessionTokenService sessionTokens;

    @BeforeEach
    void setUp() {
        byte[] secret = new byte[32];
        for (int i = 0; i < secret.length; i++) secret[i] = (byte) i;
        sessionTokens = new SessionTokenService(secret);
    }

    private Javalin app(int defaultBurst, int eventsBurst) {
        AuthMiddleware auth = new AuthMiddleware(sessionTokens);
        RateLimitMiddleware limiter = new RateLimitMiddleware(
                new RateLimiter(1, defaultBurst, 100),
                new RateLimiter(1, eventsBurst, 100));
        return Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(MAPPER, false));
            config.startup.showJavalinBanner = false;
            config.routes.before(auth::handle);
            config.routes.before(limiter::handle);
            config.routes.get("/v1/anything", ctx -> ctx.json(Map.of("ok", true)));
            config.routes.post("/v1/events", ctx -> ctx.json(Map.of("ok", true)));
            config.routes.get("/actuator/health", ctx -> ctx.json(Map.of("status", "UP")));
        });
    }

    private String bearerFor(String userId) {
        return "Bearer " + sessionTokens.mint(userId).token();
    }

    @Test
    void answers429WithRetryAfterOnceTheBurstIsSpent() {
        JavalinTest.test(app(2, 2), (server, client) -> {
            assertEquals(200, client.get("/v1/anything").code());
            assertEquals(200, client.get("/v1/anything").code());

            var throttled = client.get("/v1/anything");
            assertEquals(429, throttled.code());
            assertNotNull(throttled.headers().get("Retry-After"),
                    "clients need to know when to retry");
        });
    }

    @Test
    void neverThrottlesHealthAndMetrics() {
        // Cloud Run's probes and the Prometheus scraper are not the traffic this defends
        // against, and throttling them turns a load spike into a failed health check.
        JavalinTest.test(app(1, 1), (server, client) -> {
            for (int i = 0; i < 10; i++) {
                assertEquals(200, client.get("/actuator/health").code());
            }
        });
    }

    @Test
    void appliesAStricterBudgetToEventsThanToEverythingElse() {
        JavalinTest.test(app(10, 1), (server, client) -> {
            assertEquals(200, client.post("/v1/events", "{}").code());
            assertEquals(429, client.post("/v1/events", "{}").code());
            // The tighter events budget must not spend the caller's general allowance.
            assertEquals(200, client.get("/v1/anything").code());
        });
    }

    @Test
    void givesSignedInCallersTheirOwnBudget() {
        JavalinTest.test(app(1, 1), (server, client) -> {
            assertEquals(200, client.get("/v1/anything",
                    r -> r.header("Authorization", bearerFor("user-a"))).code());
            assertEquals(429, client.get("/v1/anything",
                    r -> r.header("Authorization", bearerFor("user-a"))).code());

            // Same IP, different user: they must not inherit user-a's exhausted bucket.
            assertEquals(200, client.get("/v1/anything",
                    r -> r.header("Authorization", bearerFor("user-b"))).code());
        });
    }

    @Test
    void keysAnonymousCallersOnTheForwardedClientIp() {
        JavalinTest.test(app(1, 1), (server, client) -> {
            assertEquals(200, client.get("/v1/anything",
                    r -> r.header("X-Forwarded-For", "203.0.113.9, 10.0.0.1")).code());
            assertEquals(429, client.get("/v1/anything",
                    r -> r.header("X-Forwarded-For", "203.0.113.9, 10.0.0.1")).code());

            // A different origin IP behind the same proxy gets its own budget.
            assertEquals(200, client.get("/v1/anything",
                    r -> r.header("X-Forwarded-For", "198.51.100.4, 10.0.0.1")).code());
        });
    }

}
