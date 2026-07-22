package app.salary.api.controller;

import app.salary.api.auth.AuthMiddleware;
import app.salary.api.auth.SessionTokenService;
import app.salary.api.store.BudgetStore;
import app.salary.api.store.InMemoryBudgetStore;
import app.salary.api.validation.RequestValidator;
import app.salary.api.validation.ValidationException;
import app.salary.common.constants.BudgetBucket;
import app.salary.common.constants.ExpenseCadence;
import app.salary.common.constants.GoalType;
import app.salary.common.dto.Budget;
import app.salary.common.dto.Expense;
import app.salary.common.dto.SavingsGoal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import io.javalin.json.JavalinJackson;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BudgetControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private BudgetStore store;
    private SessionTokenService sessionTokens;

    @BeforeEach
    void setUp() {
        store = new InMemoryBudgetStore();
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
            new BudgetController(store, new RequestValidator()).register(config.routes);
        });
    }

    private String bearerFor(String userId) {
        return "Bearer " + sessionTokens.mint(userId).token();
    }

    private static String budgetJson() throws Exception {
        return MAPPER.writeValueAsString(budget());
    }

    private static Budget budget() {
        SavingsGoal goal = new SavingsGoal();
        goal.setId("sg_1");
        goal.setType(GoalType.EMERGENCY_FUND);
        goal.setName("Emergency fund");
        goal.setTargetAmount(10000.0);
        goal.setPriority(1);

        Expense expense = new Expense();
        expense.setId("exp_1");
        expense.setName("Rent");
        expense.setAmount(1800.0);
        expense.setCadence(ExpenseCadence.MONTHLY);
        expense.setBucket(BudgetBucket.NEEDS);

        Budget budget = new Budget();
        budget.setGoals(List.of(goal));
        budget.setExpenses(List.of(expense));
        return budget;
    }

    // ── auth ─────────────────────────────────────────────────────────────────────

    @Test
    void allEndpoints_anonymous_shouldReturn401() {
        JavalinTest.test(app(), (server, client) -> {
            assertEquals(401, client.get("/v1/budget").code());
            assertEquals(401, client.put("/v1/budget", budgetJson()).code());
            assertEquals(401, client.delete("/v1/budget", null, r -> {}).code());
        });
    }

    // ── round trip ───────────────────────────────────────────────────────────────

    @Test
    void get_neverSaved_returns404() {
        JavalinTest.test(app(), (server, client) -> {
            var got = client.get("/v1/budget", r -> r.header("Authorization", bearerFor("user-1")));
            assertEquals(404, got.code());
        });
    }

    @Test
    void put_thenGet_returnsTheSavedBudget() {
        JavalinTest.test(app(), (server, client) -> {
            var saved = client.put("/v1/budget", budgetJson(),
                    r -> r.header("Authorization", bearerFor("user-1")));
            assertEquals(200, saved.code());
            JsonNode savedBody = MAPPER.readTree(saved.body().string());
            assertEquals(1, savedBody.get("goals").size());
            assertEquals("Emergency fund", savedBody.get("goals").get(0).get("name").asText());

            var got = client.get("/v1/budget", r -> r.header("Authorization", bearerFor("user-1")));
            assertEquals(200, got.code());
            JsonNode gotBody = MAPPER.readTree(got.body().string());
            assertEquals(1, gotBody.get("expenses").size());
            assertEquals("Rent", gotBody.get("expenses").get(0).get("name").asText());
        });
    }

    @Test
    void get_isScopedToTheSignedInUser() {
        store.save("someone-else", budget());

        JavalinTest.test(app(), (server, client) -> {
            var got = client.get("/v1/budget", r -> r.header("Authorization", bearerFor("user-1")));
            assertEquals(404, got.code());
        });
    }

    @Test
    void put_replacesThePreviousBudgetWholesale() {
        JavalinTest.test(app(), (server, client) -> {
            client.put("/v1/budget", budgetJson(), r -> r.header("Authorization", bearerFor("user-1")));

            Budget empty = new Budget();
            empty.setGoals(List.of());
            empty.setExpenses(List.of());
            var replaced = client.put("/v1/budget", MAPPER.writeValueAsString(empty),
                    r -> r.header("Authorization", bearerFor("user-1")));
            assertEquals(200, replaced.code());

            var got = client.get("/v1/budget", r -> r.header("Authorization", bearerFor("user-1")));
            JsonNode gotBody = MAPPER.readTree(got.body().string());
            assertEquals(0, gotBody.get("goals").size());
            assertEquals(0, gotBody.get("expenses").size());
        });
    }

    @Test
    void put_withNoGoalsOrExpenses_isValid() {
        JavalinTest.test(app(), (server, client) -> {
            var saved = client.put("/v1/budget", "{}", r -> r.header("Authorization", bearerFor("user-1")));
            assertEquals(200, saved.code());
        });
    }

    // ── delete ───────────────────────────────────────────────────────────────────

    @Test
    void delete_existingBudget_returns204ThenGetIs404() {
        JavalinTest.test(app(), (server, client) -> {
            client.put("/v1/budget", budgetJson(), r -> r.header("Authorization", bearerFor("user-1")));

            var deleted = client.delete("/v1/budget", null, r -> r.header("Authorization", bearerFor("user-1")));
            assertEquals(204, deleted.code());

            var got = client.get("/v1/budget", r -> r.header("Authorization", bearerFor("user-1")));
            assertEquals(404, got.code());
        });
    }

    @Test
    void delete_missingBudget_returns404() {
        JavalinTest.test(app(), (server, client) -> {
            var deleted = client.delete("/v1/budget", null, r -> r.header("Authorization", bearerFor("user-1")));
            assertEquals(404, deleted.code());
        });
    }

    // ── validation ───────────────────────────────────────────────────────────────

    @Test
    void put_withInvalidGoal_returns400WithNestedFieldErrors() {
        JavalinTest.test(app(), (server, client) -> {
            String invalid = MAPPER.writeValueAsString(
                    Map.of("goals", List.of(Map.of("type", "EMERGENCY_FUND"))));
            var saved = client.put("/v1/budget", invalid,
                    r -> r.header("Authorization", bearerFor("user-1")));
            assertEquals(400, saved.code());
            JsonNode errors = MAPPER.readTree(saved.body().string());
            assertTrue(errors.has("goals[0].name"));
            assertTrue(errors.has("goals[0].targetAmount"));
            assertTrue(errors.has("goals[0].priority"));
        });
    }

    @Test
    void put_withInvalidExpense_returns400WithNestedFieldErrors() {
        JavalinTest.test(app(), (server, client) -> {
            String invalid = MAPPER.writeValueAsString(
                    Map.of("expenses", List.of(Map.of("name", "Rent"))));
            var saved = client.put("/v1/budget", invalid,
                    r -> r.header("Authorization", bearerFor("user-1")));
            assertEquals(400, saved.code());
            JsonNode errors = MAPPER.readTree(saved.body().string());
            assertTrue(errors.has("expenses[0].amount"));
            assertTrue(errors.has("expenses[0].cadence"));
            assertTrue(errors.has("expenses[0].bucket"));
        });
    }
}
