package app.salary.api.controller;

import app.salary.api.auth.AuthMiddleware;
import app.salary.api.auth.SessionTokenService;
import app.salary.api.store.AccountDirectory;
import app.salary.api.store.BudgetStore;
import app.salary.api.store.CalculationStore;
import app.salary.api.store.GrantStore;
import app.salary.api.store.InMemoryAccountDirectory;
import app.salary.api.store.InMemoryEntitlementStore;
import app.salary.api.store.AccountKeyedStores;
import app.salary.api.store.InMemoryCheckInStore;
import app.salary.api.store.SubKeyedStores;
import app.salary.api.store.InMemoryLinkCodeStore;
import app.salary.api.store.InMemoryBudgetStore;
import app.salary.api.store.InMemoryCalculationStore;
import app.salary.api.store.InMemoryGrantStore;
import app.salary.api.store.InMemoryUserDirectory;
import app.salary.api.store.UserDirectory;
import app.salary.common.constants.Country;
import app.salary.common.constants.GoalType;
import app.salary.common.constants.PayCadence;
import app.salary.common.dto.Budget;
import app.salary.common.dto.CalculateRequest;
import app.salary.common.dto.CalculateResponse;
import app.salary.common.dto.CountryOptions;
import app.salary.common.dto.CountryOptionsUS;
import app.salary.common.dto.SavingsGoal;
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
    private GrantStore grants;
    private BudgetStore budgets;
    private UserDirectory users;
    private SessionTokenService sessionTokens;
    private AccountDirectory accounts;
    private InMemoryEntitlementStore entitlements;
    private InMemoryLinkCodeStore linkCodes;
    private InMemoryCheckInStore checkIns;

    @BeforeEach
    void setUp() {
        store = new InMemoryCalculationStore();
        grants = new InMemoryGrantStore();
        budgets = new InMemoryBudgetStore();
        users = new InMemoryUserDirectory();
        accounts = new InMemoryAccountDirectory();
        entitlements = new InMemoryEntitlementStore();
        linkCodes = new InMemoryLinkCodeStore();
        checkIns = new InMemoryCheckInStore();
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
            new AccountController(accounts,
                    new SubKeyedStores(store, grants, budgets, users),
                    new AccountKeyedStores(entitlements, linkCodes, checkIns))
                    .register(config.routes);
        });
    }

    private static Budget sampleBudget() {
        SavingsGoal goal = new SavingsGoal();
        goal.setId("sg_1");
        goal.setType(GoalType.EMERGENCY_FUND);
        goal.setName("Emergency fund");
        goal.setTargetAmount(10000.0);
        goal.setPriority(1);
        Budget budget = new Budget();
        budget.setGoals(java.util.List.of(goal));
        return budget;
    }

    private static app.salary.common.dto.RsuGrant sampleGrant() {
        var schedule = new app.salary.common.dto.RsuGrant.VestingSchedule();
        schedule.setPresetId("annual4");
        schedule.setTotalMonths(48);
        schedule.setCliffMonths(12);
        schedule.setFreqMonths(12);
        var grant = new app.salary.common.dto.RsuGrant();
        grant.setTicker("AAPL");
        grant.setCompany("Apple Inc.");
        grant.setSharesTotal(400.0);
        grant.setPricePerShare(232.14);
        grant.setGrantDate("2025-03-15");
        grant.setSchedule(schedule);
        return grant;
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

    private String bearerFor() {
        return "Bearer " + sessionTokens.mint("user-1").token();
    }

    @Test
    void deleteAccountRequiresAuth() {
        JavalinTest.test(app(), (server, client) -> {
            assertEquals(401, client.delete("/v1/account", null, r -> {}).code());
        });
    }

    @Test
    void deleteAccountRemovesCalculationsGrantsBudgetAndDirectoryRecord() {
        var saved = store.save("user-1", sampleRequest(), new CalculateResponse());
        store.save("user-1", sampleRequest(), new CalculateResponse());
        grants.create("user-1", sampleGrant());
        budgets.save("user-1", sampleBudget());
        users.upsertOnSignIn("user-1", "Alex Carter");
        // a second user's data must be left untouched
        store.save("user-2", sampleRequest(), new CalculateResponse());
        grants.create("user-2", sampleGrant());
        budgets.save("user-2", sampleBudget());
        users.upsertOnSignIn("user-2", "Sam Rivera");

        JavalinTest.test(app(), (server, client) -> {
            var resp = client.delete("/v1/account", null,
                    r -> r.header("Authorization", bearerFor()));
            assertEquals(204, resp.code());

            // user-1 fully wiped
            assertEquals(0, store.list("user-1", 50, null).getItems().size());
            assertTrue(store.get("user-1", saved.getId()).isEmpty());
            assertTrue(grants.list("user-1").isEmpty());
            assertTrue(budgets.get("user-1").isEmpty());
            assertTrue(users.displayName("user-1").isEmpty());

            // user-2 intact
            assertEquals(1, store.list("user-2", 50, null).getItems().size());
            assertEquals(1, grants.list("user-2").size());
            assertTrue(budgets.get("user-2").isPresent());
            assertTrue(users.displayName("user-2").isPresent());
        });
    }

    @Test
    void deleteAccountAlsoPurgesTheAccountIdentityMapping() {
        // Left behind, these would be identity records pointing at a deleted user — and the
        // migration would later resurrect an account for someone who asked to be forgotten.
        accounts.resolveOrCreate(AccountDirectory.PROVIDER_APPLE, "user-1", "Alex Carter");
        accounts.resolveOrCreate(AccountDirectory.PROVIDER_APPLE, "user-2", "Sam Rivera");
        users.upsertOnSignIn("user-1", "Alex Carter");

        JavalinTest.test(app(), (server, client) -> {
            var resp = client.delete("/v1/account", null,
                    r -> r.header("Authorization", bearerFor()));
            assertEquals(204, resp.code());

            assertTrue(accounts.findAccountId(AccountDirectory.PROVIDER_APPLE, "user-1").isEmpty());
            assertTrue(accounts.findAccountId(AccountDirectory.PROVIDER_APPLE, "user-2").isPresent());
        });
    }

    @Test
    void deleteAccountSucceedsWhenTheUserHasNoAccountMapping() {
        // Everyone who signed in before this shipped has no identity record yet.
        users.upsertOnSignIn("user-1", "Alex Carter");

        JavalinTest.test(app(), (server, client) -> {
            var resp = client.delete("/v1/account", null,
                    r -> r.header("Authorization", bearerFor()));
            assertEquals(204, resp.code());
        });
    }
}
