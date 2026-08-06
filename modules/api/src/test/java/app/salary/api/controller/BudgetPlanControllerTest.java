package app.salary.api.controller;

import app.salary.api.auth.AuthMiddleware;
import app.salary.api.auth.SessionTokenService;
import app.salary.api.client.GenerativeAiClient;
import app.salary.api.client.GenerativeAiException;
import app.salary.api.service.BudgetPlanService;
import app.salary.api.validation.RequestValidator;
import app.salary.api.validation.ValidationException;
import app.salary.common.constants.BudgetBucket;
import app.salary.common.constants.ExpenseCadence;
import app.salary.common.constants.GoalType;
import app.salary.common.constants.PayCadence;
import app.salary.common.dto.Budget;
import app.salary.common.dto.BudgetPlanRequest;
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

class BudgetPlanControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String MODEL = "gemini-3.1-flash-lite";
    private static final String VALID_JSON =
            "{\"rationale\":\"Front-load the emergency fund.\","
                    + "\"goalContributions\":[{\"goalId\":\"sg_1\",\"suggestedPerPeriodAmount\":250.0}],"
                    + "\"warnings\":[]}";

    private static final GenerativeAiClient WORKING_CLIENT =
            (model, prompt, schema) -> VALID_JSON;

    private static final GenerativeAiClient FAILING_CLIENT = (model, prompt, schema) -> {
        throw new GenerativeAiException("upstream down");
    };

    private SessionTokenService sessionTokens;

    @BeforeEach
    void setUp() {
        byte[] secret = new byte[32];
        for (int i = 0; i < secret.length; i++) secret[i] = (byte) i;
        sessionTokens = new SessionTokenService(secret);
    }

    private Javalin app(BudgetPlanService service) {
        AuthMiddleware middleware = new AuthMiddleware(sessionTokens);
        return Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(MAPPER, false));
            config.startup.showJavalinBanner = false;
            config.routes.before(middleware::handle);
            config.routes.exception(ValidationException.class, (e, ctx) ->
                    ctx.status(HttpStatus.BAD_REQUEST).json(e.getErrors()));
            new BudgetPlanController(service, new RequestValidator()).register(config.routes);
        });
    }

    private String bearerFor(String userId) {
        return "Bearer " + sessionTokens.mint(userId).token();
    }

    private static String requestJson() throws Exception {
        SavingsGoal goal = new SavingsGoal();
        goal.setId("sg_1");
        goal.setType(GoalType.EMERGENCY_FUND);
        goal.setName("Emergency fund");
        goal.setTargetAmount(18000.0);
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

        BudgetPlanRequest request = new BudgetPlanRequest();
        request.setBudget(budget);
        request.setPayFrequency(PayCadence.BIWEEKLY);
        request.setNetIncomePerPeriod(2400.0);
        return MAPPER.writeValueAsString(request);
    }

    @Test
    void generate_serviceUnavailable_returns503() {
        JavalinTest.test(app(null), (server, client) -> {
            var response = client.post("/v1/budget/plan", requestJson(), authed());
            assertEquals(503, response.code());
        });
    }

    @Test
    void generate_anonymous_returns401() {
        // Every call bills a Vertex AI request, so this must never be reachable without
        // a session. Checked before the service-null branch so an anonymous caller cannot
        // distinguish "Vertex AI is off" from "you are not signed in".
        BudgetPlanService service = new BudgetPlanService(WORKING_CLIENT, MAPPER, MODEL);
        JavalinTest.test(app(service), (server, client) ->
                assertEquals(401, client.post("/v1/budget/plan", requestJson()).code()));
    }

    @Test
    void generate_anonymousWithVertexDisabled_stillReturns401() {
        JavalinTest.test(app(null), (server, client) ->
                assertEquals(401, client.post("/v1/budget/plan", requestJson()).code()));
    }

    @Test
    void generate_signedIn_stillWorks() {
        BudgetPlanService service = new BudgetPlanService(WORKING_CLIENT, MAPPER, MODEL);
        JavalinTest.test(app(service), (server, client) -> {
            var response = client.post("/v1/budget/plan", requestJson(), authed());
            assertEquals(200, response.code());
            JsonNode body = MAPPER.readTree(response.body().string());
            assertEquals("Front-load the emergency fund.", body.get("rationale").asText());
            assertEquals("sg_1", body.get("goalContributions").get(0).get("goalId").asText());
        });
    }

    @Test
    void generate_upstreamFailure_returns503() {
        BudgetPlanService service = new BudgetPlanService(FAILING_CLIENT, MAPPER, MODEL);
        JavalinTest.test(app(service), (server, client) -> {
            var response = client.post("/v1/budget/plan", requestJson(), authed());
            assertEquals(503, response.code());
        });
    }

    @Test
    void generate_invalidRequest_returns400() {
        BudgetPlanService service = new BudgetPlanService(WORKING_CLIENT, MAPPER, MODEL);
        JavalinTest.test(app(service), (server, client) -> {
            String invalid = MAPPER.writeValueAsString(Map.of("netIncomePerPeriod", 2400.0));
            var response = client.post("/v1/budget/plan", invalid, authed());
            assertEquals(400, response.code());
            JsonNode errors = MAPPER.readTree(response.body().string());
            assertTrue(errors.has("budget"));
            assertTrue(errors.has("payFrequency"));
        });
    }

    private java.util.function.Consumer<io.javalin.testtools.Request.Builder> authed() {
        return r -> r.header("Authorization", bearerFor("user-1"));
    }
}
