package app.salary.api.controller;

import app.salary.api.auth.AuthMiddleware;
import app.salary.api.auth.SessionTokenService;
import app.salary.api.store.AccountDirectory;
import app.salary.api.store.EventRecord;
import app.salary.api.store.InMemoryAccountDirectory;
import app.salary.api.store.InMemoryEventStore;
import app.salary.api.validation.RequestValidator;
import app.salary.api.validation.ValidationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import io.javalin.json.JavalinJackson;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventsControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private InMemoryEventStore events;
    private AccountDirectory accounts;
    private SessionTokenService sessionTokens;

    @BeforeEach
    void setUp() {
        events = new InMemoryEventStore();
        accounts = new InMemoryAccountDirectory();
        byte[] secret = new byte[32];
        for (int i = 0; i < secret.length; i++) secret[i] = (byte) i;
        sessionTokens = new SessionTokenService(secret);
    }

    private Javalin app() {
        AuthMiddleware middleware = new AuthMiddleware(sessionTokens);
        return Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(MAPPER, false));
            config.startup.showJavalinBanner = false;
            config.routes.before(middleware::handle);
            config.routes.exception(ValidationException.class, (e, ctx) ->
                    ctx.status(HttpStatus.BAD_REQUEST).json(e.getErrors()));
            new EventsController(events, accounts, new RequestValidator()).register(config.routes);
        });
    }

    private String bearerFor(String userId) {
        return "Bearer " + sessionTokens.mint(userId).token();
    }

    /** Most cases only need *a* signed-in caller, not a specific one. */
    private java.util.function.Consumer<io.javalin.testtools.Request.Builder> authed() {
        return r -> r.header("Authorization", bearerFor("apple-sub-1"));
    }

    private static String batch(Object... eventMaps) throws Exception {
        return MAPPER.writeValueAsString(Map.of(
                "deviceId", "device-1",
                "client", "ios/1.9.0",
                "events", List.of(eventMaps)));
    }

    @Test
    void rejectsAnonymousBatches() throws Exception {
        String body = batch(Map.of("name", "session_start"));

        JavalinTest.test(app(), (server, client) -> {
            assertEquals(401, client.post("/v1/events", body).code());
            assertTrue(events.all().isEmpty());
        });
    }

    @Test
    void storesTheBatchEnvelopeAlongsideEachEvent() throws Exception {
        accounts.resolveOrCreate(AccountDirectory.PROVIDER_APPLE, "apple-sub-1", "Ben");
        String body = batch(Map.of("name", "session_start"));

        JavalinTest.test(app(), (server, client) -> {
            var resp = client.post("/v1/events", body, authed());
            assertEquals(202, resp.code());
            assertEquals(1, MAPPER.readTree(resp.body().string()).get("accepted").asInt());

            EventRecord stored = events.all().getFirst();
            assertEquals("session_start", stored.name());
            // deviceId still matters even with auth: one account spans several devices.
            assertEquals("device-1", stored.deviceId());
            assertEquals("ios/1.9.0", stored.client());
        });
    }

    @Test
    void keysSignedInEventsOnAccountIdRatherThanTheProviderSub() throws Exception {
        String accountId = accounts.resolveOrCreate(AccountDirectory.PROVIDER_APPLE, "apple-sub-1", "Ben");
        String body = batch(Map.of("name", "calculation_completed"));

        JavalinTest.test(app(), (server, client) -> {
            var resp = client.post("/v1/events", body,
                    r -> r.header("Authorization", bearerFor("apple-sub-1")));
            assertEquals(202, resp.code());

            EventRecord stored = events.all().getFirst();
            assertEquals(accountId, stored.accountId());
            // The sub must not leak into the store, or this collection needs migrating too.
            assertTrue(events.all().stream().noneMatch(e -> "apple-sub-1".equals(e.accountId())));
        });
    }

    @Test
    void storesEventsFromUsersWhoPredateTheIdentitySchema() throws Exception {
        // Signed in, but no identity record yet — a missing accountId is normal, not an error.
        String body = batch(Map.of("name", "session_start"));

        JavalinTest.test(app(), (server, client) -> {
            var resp = client.post("/v1/events", body,
                    r -> r.header("Authorization", bearerFor("never-mapped-sub")));
            assertEquals(202, resp.code());
            assertNull(events.all().getFirst().accountId());
            assertEquals("device-1", events.all().getFirst().deviceId());
        });
    }

    @Test
    void rejectsDecimalPropertyValues() throws Exception {
        String body = batch(Map.of("name", "calculation_completed",
                "properties", Map.of("net_bucket", 2847.13)));

        JavalinTest.test(app(), (server, client) -> {
            var resp = client.post("/v1/events", body, authed());
            assertEquals(400, resp.code());
            assertTrue(resp.body().string().contains("bucketed"));
            assertTrue(events.all().isEmpty(), "nothing should be stored when the batch is rejected");
        });
    }

    @Test
    void rejectsNumericAmountBearingProperties() throws Exception {
        String body = batch(Map.of("name", "calculation_completed",
                "properties", Map.of("net_pay", 100000)));

        JavalinTest.test(app(), (server, client) -> {
            var resp = client.post("/v1/events", body, authed());
            assertEquals(400, resp.code());
            assertTrue(events.all().isEmpty());
        });
    }

    @Test
    void acceptsBucketedAmountStrings() throws Exception {
        String body = batch(Map.of("name", "calculation_completed",
                "properties", Map.of("net_pay_bucket", "2000-3000", "state", "TX", "count", 3)));

        JavalinTest.test(app(), (server, client) -> {
            assertEquals(202, client.post("/v1/events", body, authed()).code());
            Map<String, Object> props = events.all().getFirst().properties();
            assertEquals("2000-3000", props.get("net_pay_bucket"));
            assertEquals("TX", props.get("state"));
            assertEquals(3, props.get("count"));
        });
    }

    @Test
    void rejectsAnEmptyBatch() throws Exception {
        String body = MAPPER.writeValueAsString(Map.of("deviceId", "device-1", "events", List.of()));
        JavalinTest.test(app(), (server, client) ->
                assertEquals(400, client.post("/v1/events", body, authed()).code()));
    }

    @Test
    void rejectsABatchWithoutADeviceId() throws Exception {
        String body = MAPPER.writeValueAsString(Map.of(
                "events", List.of(Map.of("name", "session_start"))));
        JavalinTest.test(app(), (server, client) ->
                assertEquals(400, client.post("/v1/events", body, authed()).code()));
    }

    @Test
    void rejectsMalformedEventNames() throws Exception {
        String body = batch(Map.of("name", "Calculation Completed"));
        JavalinTest.test(app(), (server, client) ->
                assertEquals(400, client.post("/v1/events", body, authed()).code()));
    }

    @Test
    void rejectsOversizedBatches() throws Exception {
        List<Map<String, String>> tooMany = java.util.stream.IntStream.range(0, 51)
                .mapToObj(i -> Map.of("name", "session_start"))
                .toList();
        String body = MAPPER.writeValueAsString(Map.of("deviceId", "device-1", "events", tooMany));
        JavalinTest.test(app(), (server, client) ->
                assertEquals(400, client.post("/v1/events", body, authed()).code()));
    }

    @Test
    void storesEveryEventInAMultiEventBatch() throws Exception {
        String body = batch(
                Map.of("name", "session_start"),
                Map.of("name", "onboarding_step", "properties", Map.of("step", 2)),
                Map.of("name", "onboarding_completed"));

        JavalinTest.test(app(), (server, client) -> {
            var resp = client.post("/v1/events", body, authed());
            assertEquals(202, resp.code());
            assertEquals(3, MAPPER.readTree(resp.body().string()).get("accepted").asInt());
            assertEquals(List.of("session_start", "onboarding_step", "onboarding_completed"),
                    events.all().stream().map(EventRecord::name).toList());
        });
    }

    @Test
    void usesTheClientTimestampWhenSuppliedAndFallsBackWhenNot() throws Exception {
        String body = batch(
                Map.of("name", "session_start", "occurredAt", "2026-08-05T10:15:30Z"),
                Map.of("name", "session_start", "occurredAt", "not-a-timestamp"));

        JavalinTest.test(app(), (server, client) -> {
            assertEquals(202, client.post("/v1/events", body, authed()).code());
            assertEquals("2026-08-05T10:15:30Z", events.all().getFirst().occurredAt().toString());
            // A bad clock should not cost us the event.
            assertEquals(events.all().get(1).receivedAt(), events.all().get(1).occurredAt());
        });
    }

    @Test
    void truncatesOverlongPropertyValues() throws Exception {
        String body = batch(Map.of("name", "session_start",
                "properties", Map.of("note", "x".repeat(500))));

        JavalinTest.test(app(), (server, client) -> {
            assertEquals(202, client.post("/v1/events", body, authed()).code());
            assertEquals(100, ((String) events.all().getFirst().properties().get("note")).length());
        });
    }
}
