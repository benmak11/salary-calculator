package app.salary.api.controller;

import app.salary.api.auth.AuthMiddleware;
import app.salary.api.auth.SessionTokenService;
import app.salary.api.store.AccountDirectory;
import app.salary.api.store.InMemoryAccountDirectory;
import app.salary.api.store.InMemoryCheckInStore;
import app.salary.api.validation.RequestValidator;
import app.salary.api.validation.ValidationException;
import app.salary.common.dto.PaycheckCheckIn;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import io.javalin.json.JavalinJackson;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckInControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private InMemoryCheckInStore store;
    private InMemoryAccountDirectory accounts;
    private SessionTokenService sessionTokens;

    @BeforeEach
    void setUp() {
        store = new InMemoryCheckInStore();
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
            new CheckInController(store, accounts, new RequestValidator()).register(config.routes);
        });
    }

    private Consumer<io.javalin.testtools.Request.Builder> as(String sub) {
        return r -> r.header("Authorization", "Bearer " + sessionTokens.mint(sub).token());
    }

    private String signedIn() {
        return accounts.resolveOrCreate(AccountDirectory.PROVIDER_APPLE, "apple-1", "Alex");
    }

    private static Map<String, Object> body(String payDate, double actualNet) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("payDate", payDate);
        b.put("actualNet", actualNet);
        return b;
    }

    @Test
    void recordsAPaydayAndKeysItOnTheAccountNotTheSub() {
        String accountId = signedIn();

        JavalinTest.test(app(), (server, client) -> {
            var response = client.post("/v1/checkins", body("2026-08-14", 2847.13), as("apple-1"));
            assertEquals(201, response.code());

            JsonNode json = MAPPER.readTree(response.body().string());
            assertNotNull(json.get("id").asText());
            assertEquals("2026-08-14", json.get("payDate").asText());
            // Server-stamped: a wrong or hostile client clock must not decide this.
            assertNotNull(json.get("recordedAt"));

            // Keyed on accountId is what keeps this collection out of the B-1b migration.
            assertEquals(1, store.list(accountId, 10).size());
            assertTrue(store.list("apple-1", 10).isEmpty(), "must not be keyed on the provider sub");
        });
    }

    @Test
    void everyRouteRefusesAnonymousCallers() {
        JavalinTest.test(app(), (server, client) -> {
            assertEquals(401, client.post("/v1/checkins", body("2026-08-14", 100.0)).code());
            assertEquals(401, client.get("/v1/checkins").code());
            assertEquals(401, client.delete("/v1/checkins/abc").code());
        });
    }

    @Test
    void confirmingTheSamePaydayTwiceCorrectsRatherThanDuplicates() {
        String accountId = signedIn();

        JavalinTest.test(app(), (server, client) -> {
            var first = client.post("/v1/checkins", body("2026-08-14", 2847.13), as("apple-1"));
            String firstId = MAPPER.readTree(first.body().string()).get("id").asText();

            var corrected = client.post("/v1/checkins", body("2026-08-14", 2900.00), as("apple-1"));
            assertEquals(201, corrected.code());

            // A second row for the same payday would double-count in every YTD total.
            assertEquals(1, store.list(accountId, 10).size());
            assertEquals(2900.00, store.list(accountId, 10).getFirst().getActualNet());
            // The id survives the correction so a client holding it stays valid.
            assertEquals(firstId, MAPPER.readTree(corrected.body().string()).get("id").asText());
        });
    }

    @Test
    void listsNewestPaydayFirst() {
        signedIn();

        JavalinTest.test(app(), (server, client) -> {
            client.post("/v1/checkins", body("2026-07-17", 100.0), as("apple-1"));
            client.post("/v1/checkins", body("2026-08-14", 300.0), as("apple-1"));
            client.post("/v1/checkins", body("2026-07-31", 200.0), as("apple-1"));

            var response = client.get("/v1/checkins", as("apple-1"));
            assertEquals(200, response.code());
            JsonNode json = MAPPER.readTree(response.body().string());
            assertEquals(3, json.get("count").asInt());
            assertEquals("2026-08-14", json.get("checkIns").get(0).get("payDate").asText());
            assertEquals("2026-07-17", json.get("checkIns").get(2).get("payDate").asText());
        });
    }

    @Test
    void rejectsAMalformedPayDate() {
        signedIn();
        // The pay date is the storage key, so a bad one would create an unreachable entry.
        JavalinTest.test(app(), (server, client) -> {
            assertEquals(400, client.post("/v1/checkins", body("14/08/2026", 100.0), as("apple-1")).code());
            assertEquals(400, client.post("/v1/checkins", body("not-a-date", 100.0), as("apple-1")).code());
        });
    }

    @Test
    void rejectsAMissingOrNegativeAmount() {
        signedIn();
        JavalinTest.test(app(), (server, client) -> {
            assertEquals(400, client.post("/v1/checkins", Map.of("payDate", "2026-08-14"), as("apple-1")).code());
            assertEquals(400, client.post("/v1/checkins", body("2026-08-14", -5.0), as("apple-1")).code());
        });
    }

    @Test
    void deletesOnlyTheRequestedEntry() {
        String accountId = signedIn();

        JavalinTest.test(app(), (server, client) -> {
            var created = client.post("/v1/checkins", body("2026-08-14", 100.0), as("apple-1"));
            String id = MAPPER.readTree(created.body().string()).get("id").asText();
            client.post("/v1/checkins", body("2026-07-31", 200.0), as("apple-1"));

            assertEquals(204, client.delete("/v1/checkins/" + id, null, as("apple-1")).code());
            assertEquals(1, store.list(accountId, 10).size());
            assertEquals(404, client.delete("/v1/checkins/" + id, null, as("apple-1")).code());
        });
    }

    @Test
    void oneAccountCannotSeeOrDeleteAnothers() {
        signedIn();
        accounts.resolveOrCreate(AccountDirectory.PROVIDER_GOOGLE, "google-2", "Sam");

        JavalinTest.test(app(), (server, client) -> {
            var mine = client.post("/v1/checkins", body("2026-08-14", 100.0), as("apple-1"));
            String id = MAPPER.readTree(mine.body().string()).get("id").asText();

            assertEquals(0, MAPPER.readTree(client.get("/v1/checkins", as("google-2")).body().string())
                    .get("count").asInt());
            assertEquals(404, client.delete("/v1/checkins/" + id, null, as("google-2")).code());
        });
    }

    @Test
    void aCallerWithNoAccountRecordIsAskedToSignInAgain() {
        // Pre-identity-schema users have no accountId. 409 rather than 500, and the next
        // sign-in mints one.
        JavalinTest.test(app(), (server, client) ->
                assertEquals(409, client.post("/v1/checkins", body("2026-08-14", 100.0),
                        as("never-mapped")).code()));
    }

    @Test
    void carriesTheOptionalBreakdownTheYtdTrackerWillNeed() {
        String accountId = signedIn();
        Map<String, Object> full = new LinkedHashMap<>(body("2026-08-14", 2847.13));
        full.put("expectedNet", 2920.00);
        full.put("grossPay", 4000.00);
        full.put("federalTax", 506.54);
        full.put("retirement401k", 240.00);
        full.put("hsaContribution", 150.00);

        JavalinTest.test(app(), (server, client) -> {
            assertEquals(201, client.post("/v1/checkins", full, as("apple-1")).code());

            PaycheckCheckIn stored = store.list(accountId, 10).getFirst();
            assertEquals(2920.00, stored.getExpectedNet());
            assertEquals(4000.00, stored.getGrossPay());
            assertEquals(240.00, stored.getRetirement401k());
        });
    }

    @Test
    void aOneTapCheckInNeedsOnlyTheNet() {
        String accountId = signedIn();
        // The breakdown is optional on purpose: one tap on payday is the interaction.
        JavalinTest.test(app(), (server, client) -> {
            assertEquals(201, client.post("/v1/checkins", body("2026-08-14", 2847.13), as("apple-1")).code());
            PaycheckCheckIn stored = store.list(accountId, 10).getFirst();
            assertEquals(2847.13, stored.getActualNet());
            assertNull(stored.getGrossPay(), "a one-tap check-in stores no breakdown");
        });
    }

    @Test
    void limitIsClampedRatherThanTrusted() {
        signedIn();
        JavalinTest.test(app(), (server, client) -> {
            client.post("/v1/checkins", body("2026-08-14", 100.0), as("apple-1"));
            for (String limit : new String[]{"0", "-1", "abc", "99999"}) {
                var response = client.get("/v1/checkins?limit=" + limit, as("apple-1"));
                assertEquals(200, response.code(), "limit=" + limit + " must not blow up");
            }
        });
    }
}
