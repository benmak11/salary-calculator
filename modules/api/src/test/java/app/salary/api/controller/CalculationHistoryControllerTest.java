package app.salary.api.controller;

import app.salary.api.auth.AuthMiddleware;
import app.salary.api.auth.SessionTokenService;
import app.salary.api.store.CalculationStore;
import app.salary.api.store.InMemoryCalculationStore;
import app.salary.common.constants.Country;
import app.salary.common.constants.PayCadence;
import app.salary.common.dto.CalculateRequest;
import app.salary.common.dto.CalculateResponse;
import app.salary.common.dto.CountryOptions;
import app.salary.common.dto.CountryOptionsUS;
import app.salary.common.dto.LineItem;
import app.salary.common.dto.LineItemCategory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculationHistoryControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CalculationStore store;
    private SessionTokenService sessionTokens;

    @BeforeEach
    void setUp() {
        store = new InMemoryCalculationStore();
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
            new CalculationHistoryController(store).register(config.routes);
        });
    }

    private static CalculateRequest sampleRequest(String stateCode) {
        CalculateRequest r = new CalculateRequest();
        r.setCountry(Country.US);
        r.setTaxYear(2025);
        r.setCadence(PayCadence.BIWEEKLY);
        CountryOptionsUS us = new CountryOptionsUS();
        us.setState(stateCode);
        CountryOptions opts = new CountryOptions();
        opts.setUs(us);
        r.setCountryOptions(opts);
        return r;
    }

    private static CalculateResponse sampleResponse(double net, double gross) {
        CalculateResponse resp = new CalculateResponse();
        resp.setGrossPerCadence(gross);
        resp.setNetPerCadence(net);
        resp.setCurrency("USD");
        resp.setLineItems(List.of(
                new LineItem("Federal Income Tax", 200.0, LineItemCategory.TAX_FEDERAL),
                new LineItem("Medical Premium",      50.0, LineItemCategory.PRE_TAX_BENEFIT)));
        return resp;
    }

    private String bearerFor(String userId) {
        return "Bearer " + sessionTokens.mint(userId).token();
    }

    @Test
    void listRequiresAuth() {
        JavalinTest.test(app(), (server, client) -> {
            assertEquals(401, client.get("/v1/calculations").code());
        });
    }

    @Test
    void getRequiresAuth() {
        JavalinTest.test(app(), (server, client) -> {
            assertEquals(401, client.get("/v1/calculations/anything").code());
        });
    }

    @Test
    void deleteRequiresAuth() {
        JavalinTest.test(app(), (server, client) -> {
            assertEquals(401, client.delete("/v1/calculations/anything", null,
                    r -> {}).code());
        });
    }

    @Test
    void listReturnsSavedSummariesForOwner() {
        var saved = store.save("user-1", sampleRequest("CA"), sampleResponse(1500.0, 2000.0));
        JavalinTest.test(app(), (server, client) -> {
            var resp = client.get("/v1/calculations",
                    r -> r.header("Authorization", bearerFor("user-1")));
            assertEquals(200, resp.code());
            JsonNode body = MAPPER.readTree(resp.body().string());
            assertTrue(body.get("items").isArray());
            assertEquals(1, body.get("items").size());
            assertEquals(saved.getId(), body.get("items").get(0).get("id").asText());
            assertEquals("California", body.get("items").get(0).get("stateName").asText());
        });
    }

    @Test
    void getReturnsFullDetailForOwner() {
        var saved = store.save("user-1", sampleRequest("NY"), sampleResponse(1200.0, 2000.0));
        JavalinTest.test(app(), (server, client) -> {
            var resp = client.get("/v1/calculations/" + saved.getId(),
                    r -> r.header("Authorization", bearerFor("user-1")));
            assertEquals(200, resp.code());
            JsonNode body = MAPPER.readTree(resp.body().string());
            assertEquals(saved.getId(), body.get("summary").get("id").asText());
            assertEquals("NY", body.get("request").get("countryOptions").get("US").get("state").asText());
            assertEquals(1200.0, body.get("response").get("netPerCadence").asDouble());
        });
    }

    @Test
    void getReturns404ForUnknownIdOrCrossUserAccess() {
        var saved = store.save("user-1", sampleRequest("CA"), sampleResponse(1500.0, 2000.0));
        JavalinTest.test(app(), (server, client) -> {
            // unknown id
            assertEquals(404, client.get("/v1/calculations/does-not-exist",
                    r -> r.header("Authorization", bearerFor("user-1"))).code());
            // wrong user → 404 (we don't leak existence)
            assertEquals(404, client.get("/v1/calculations/" + saved.getId(),
                    r -> r.header("Authorization", bearerFor("user-2"))).code());
        });
    }

    @Test
    void deleteRemovesOnlyOwnerDoc() {
        var saved = store.save("user-1", sampleRequest("CA"), sampleResponse(1500.0, 2000.0));
        JavalinTest.test(app(), (server, client) -> {
            // wrong user → 404, doc still there
            var wrong = client.delete("/v1/calculations/" + saved.getId(), null,
                    r -> r.header("Authorization", bearerFor("user-2")));
            assertEquals(404, wrong.code());
            assertTrue(store.get("user-1", saved.getId()).isPresent());

            // owner → 204
            var owner = client.delete("/v1/calculations/" + saved.getId(), null,
                    r -> r.header("Authorization", bearerFor("user-1")));
            assertEquals(204, owner.code());
            assertTrue(store.get("user-1", saved.getId()).isEmpty());
        });
    }
}
