package app.salary.api.controller;

import app.salary.api.auth.AuthMiddleware;
import app.salary.api.auth.SessionTokenService;
import app.salary.api.store.GrantStore;
import app.salary.api.store.InMemoryGrantStore;
import app.salary.api.validation.RequestValidator;
import app.salary.api.validation.ValidationException;
import app.salary.common.dto.RsuGrant;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import io.javalin.json.JavalinJackson;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GrantsControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GrantStore store;
    private SessionTokenService sessionTokens;

    @BeforeEach
    void setUp() {
        store = new InMemoryGrantStore();
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
            new GrantsController(store, new RequestValidator()).register(config.routes);
        });
    }

    private String bearerFor(String userId) {
        return "Bearer " + sessionTokens.mint(userId).token();
    }

    private static String grantJson() throws Exception {
        RsuGrant.VestingSchedule schedule = new RsuGrant.VestingSchedule();
        schedule.setPresetId("annual4");
        schedule.setTotalMonths(48);
        schedule.setCliffMonths(12);
        schedule.setFreqMonths(12);
        RsuGrant grant = new RsuGrant();
        grant.setTicker("AAPL");
        grant.setCompany("Apple Inc.");
        grant.setSharesTotal(400.0);
        grant.setPricePerShare(232.14);
        grant.setGrantDate("2025-03-15");
        grant.setSchedule(schedule);
        return MAPPER.writeValueAsString(grant);
    }

    // ── auth ─────────────────────────────────────────────────────────────────────

    @Test
    void allEndpoints_anonymous_shouldReturn401() {
        JavalinTest.test(app(), (server, client) -> {
            assertEquals(401, client.get("/v1/grants").code());
            assertEquals(401, client.post("/v1/grants", grantJson()).code());
            assertEquals(401, client.put("/v1/grants/g_x", grantJson()).code());
            assertEquals(401, client.delete("/v1/grants/g_x", null, r -> {}).code());
        });
    }

    // ── CRUD round trip ──────────────────────────────────────────────────────────

    @Test
    void create_thenList_returnsSavedGrant() {
        JavalinTest.test(app(), (server, client) -> {
            var created = client.post("/v1/grants", grantJson(),
                    r -> r.header("Authorization", bearerFor("user-1")));
            assertEquals(201, created.code());
            JsonNode createdBody = MAPPER.readTree(created.body().string());
            String id = createdBody.get("id").asText();
            assertFalse(id.isBlank());
            assertEquals("AAPL", createdBody.get("ticker").asText());

            var listed = client.get("/v1/grants",
                    r -> r.header("Authorization", bearerFor("user-1")));
            assertEquals(200, listed.code());
            JsonNode items = MAPPER.readTree(listed.body().string()).get("items");
            assertEquals(1, items.size());
            assertEquals(id, items.get(0).get("id").asText());
        });
    }

    @Test
    void list_isScopedToTheSignedInUser() throws Exception {
        store.create("someone-else", MAPPER.readValue(grantJson(), RsuGrant.class));

        JavalinTest.test(app(), (server, client) -> {
            var listed = client.get("/v1/grants",
                    r -> r.header("Authorization", bearerFor("user-1")));
            assertEquals(200, listed.code());
            assertEquals(0, MAPPER.readTree(listed.body().string()).get("items").size());
        });
    }

    @Test
    void update_existingGrant_replacesIt() {
        JavalinTest.test(app(), (server, client) -> {
            var created = client.post("/v1/grants", grantJson(),
                    r -> r.header("Authorization", bearerFor("user-1")));
            String id = MAPPER.readTree(created.body().string()).get("id").asText();

            RsuGrant edited = MAPPER.readValue(grantJson(), RsuGrant.class);
            edited.setSharesTotal(500.0);
            var updated = client.put("/v1/grants/" + id, MAPPER.writeValueAsString(edited),
                    r -> r.header("Authorization", bearerFor("user-1")));
            assertEquals(200, updated.code());
            JsonNode body = MAPPER.readTree(updated.body().string());
            assertEquals(id, body.get("id").asText());
            assertEquals(500.0, body.get("sharesTotal").asDouble(), 0.01);
        });
    }

    @Test
    void update_missingGrant_returns404() {
        JavalinTest.test(app(), (server, client) -> {
            var updated = client.put("/v1/grants/g_missing", grantJson(),
                    r -> r.header("Authorization", bearerFor("user-1")));
            assertEquals(404, updated.code());
        });
    }

    @Test
    void delete_existingGrant_returns204ThenListIsEmpty() {
        JavalinTest.test(app(), (server, client) -> {
            var created = client.post("/v1/grants", grantJson(),
                    r -> r.header("Authorization", bearerFor("user-1")));
            String id = MAPPER.readTree(created.body().string()).get("id").asText();

            var deleted = client.delete("/v1/grants/" + id, null,
                    r -> r.header("Authorization", bearerFor("user-1")));
            assertEquals(204, deleted.code());

            var listed = client.get("/v1/grants",
                    r -> r.header("Authorization", bearerFor("user-1")));
            assertEquals(0, MAPPER.readTree(listed.body().string()).get("items").size());
        });
    }

    @Test
    void delete_missingGrant_returns404() {
        JavalinTest.test(app(), (server, client) -> {
            var deleted = client.delete("/v1/grants/g_missing", null,
                    r -> r.header("Authorization", bearerFor("user-1")));
            assertEquals(404, deleted.code());
        });
    }

    // ── validation ───────────────────────────────────────────────────────────────

    @Test
    void create_withoutRequiredFields_returns400WithFieldErrors() {
        JavalinTest.test(app(), (server, client) -> {
            String invalid = MAPPER.writeValueAsString(Map.of("ticker", "AAPL"));
            var created = client.post("/v1/grants", invalid,
                    r -> r.header("Authorization", bearerFor("user-1")));
            assertEquals(400, created.code());
            JsonNode errors = MAPPER.readTree(created.body().string());
            assertTrue(errors.has("sharesTotal"));
            assertTrue(errors.has("pricePerShare"));
            assertTrue(errors.has("grantDate"));
            assertTrue(errors.has("schedule"));
        });
    }

    @Test
    void create_withInvalidSchedule_returns400() {
        JavalinTest.test(app(), (server, client) -> {
            RsuGrant grant = MAPPER.readValue(grantJson(), RsuGrant.class);
            grant.getSchedule().setTotalMonths(0);
            var created = client.post("/v1/grants", MAPPER.writeValueAsString(grant),
                    r -> r.header("Authorization", bearerFor("user-1")));
            assertEquals(400, created.code());
            assertTrue(MAPPER.readTree(created.body().string()).has("schedule.totalMonths"));
        });
    }
}
