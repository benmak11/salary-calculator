package app.salary.api.controller;

import app.salary.api.auth.AuthMiddleware;
import app.salary.api.auth.SessionTokenService;
import app.salary.api.store.CalculationStore;
import app.salary.api.store.InMemoryCalculationStore;
import app.salary.api.store.InMemoryUserDirectory;
import app.salary.api.store.UserDirectory;
import app.salary.common.constants.Country;
import app.salary.common.constants.PayCadence;
import app.salary.common.dto.CalculateRequest;
import app.salary.common.dto.CalculateResponse;
import app.salary.common.dto.CountryOptions;
import app.salary.common.dto.CountryOptionsUS;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CalculationStore store;
    private UserDirectory users;
    private SessionTokenService sessionTokens;

    @BeforeEach
    void setUp() {
        store = new InMemoryCalculationStore();
        users = new InMemoryUserDirectory();
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
            new AccountController(store, users).register(config.routes);
        });
    }

    private static CalculateRequest sampleRequest() {
        CalculateRequest r = new CalculateRequest();
        r.setCountry(Country.US);
        r.setTaxYear(2025);
        r.setCadence(PayCadence.BIWEEKLY);
        CountryOptionsUS us = new CountryOptionsUS();
        us.setState("CA");
        CountryOptions opts = new CountryOptions();
        opts.setUs(us);
        r.setCountryOptions(opts);
        return r;
    }

    private String bearerFor(String userId) {
        return "Bearer " + sessionTokens.mint(userId).token();
    }

    @Test
    void deleteAccountRequiresAuth() {
        JavalinTest.test(app(), (server, client) -> {
            assertEquals(401, client.delete("/v1/account", null, r -> {}).code());
        });
    }

    @Test
    void deleteAccountRemovesCalculationsAndDirectoryRecord() {
        var saved = store.save("user-1", sampleRequest(), new CalculateResponse());
        store.save("user-1", sampleRequest(), new CalculateResponse());
        users.upsertOnSignIn("user-1", "Alex Carter");
        // a second user's data must be left untouched
        store.save("user-2", sampleRequest(), new CalculateResponse());
        users.upsertOnSignIn("user-2", "Sam Rivera");

        JavalinTest.test(app(), (server, client) -> {
            var resp = client.delete("/v1/account", null,
                    r -> r.header("Authorization", bearerFor("user-1")));
            assertEquals(204, resp.code());

            // user-1 fully wiped
            assertEquals(0, store.list("user-1", 50, null).getItems().size());
            assertTrue(store.get("user-1", saved.getId()).isEmpty());
            assertTrue(users.displayName("user-1").isEmpty());

            // user-2 intact
            assertEquals(1, store.list("user-2", 50, null).getItems().size());
            assertTrue(users.displayName("user-2").isPresent());
        });
    }
}
