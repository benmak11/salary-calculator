package app.salary.api.service;

import app.salary.api.client.GenerativeAiClient;
import app.salary.api.client.GenerativeAiException;
import app.salary.common.constants.BudgetBucket;
import app.salary.common.constants.ExpenseCadence;
import app.salary.common.constants.GoalType;
import app.salary.common.constants.PayCadence;
import app.salary.common.dto.Budget;
import app.salary.common.dto.BudgetPlan;
import app.salary.common.dto.BudgetPlanRequest;
import app.salary.common.dto.Expense;
import app.salary.common.dto.SavingsGoal;
import app.salary.common.dto.Windfall;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetPlanServiceTest {

    @Mock
    private GenerativeAiClient client;

    private static final String MODEL = "gemini-3.1-flash-lite";

    private BudgetPlanService service;

    @BeforeEach
    void setUp() {
        service = new BudgetPlanService(client, new ObjectMapper(), MODEL);
    }

    private static BudgetPlanRequest request() {
        SavingsGoal goal = new SavingsGoal();
        goal.setId("sg_1");
        goal.setType(GoalType.EMERGENCY_FUND);
        goal.setName("Emergency fund");
        goal.setTargetAmount(18000.0);
        goal.setPriority(1);
        goal.setCurrentSaved(1000.0);

        Expense expense = new Expense();
        expense.setId("exp_1");
        expense.setName("Rent");
        expense.setAmount(1800.0);
        expense.setCadence(ExpenseCadence.MONTHLY);
        expense.setBucket(BudgetBucket.NEEDS);

        Budget budget = new Budget();
        budget.setGoals(List.of(goal));
        budget.setExpenses(List.of(expense));

        Windfall windfall = new Windfall();
        windfall.setLabel("March bonus");
        windfall.setNetAmount(7035.0);
        windfall.setDate("2026-03-15");

        BudgetPlanRequest request = new BudgetPlanRequest();
        request.setBudget(budget);
        request.setPayFrequency(PayCadence.BIWEEKLY);
        request.setNetIncomePerPeriod(2400.0);
        request.setWindfalls(List.of(windfall));
        return request;
    }

    @Test
    void generatePlan_parsesModelJsonIntoBudgetPlan() {
        String json = "{\"rationale\":\"Front-load the emergency fund.\","
                + "\"goalContributions\":[{\"goalId\":\"sg_1\",\"suggestedPerPeriodAmount\":250.0}],"
                + "\"warnings\":[]}";
        when(client.generateJson(anyString(), anyString(), any(Schema.class))).thenReturn(json);

        BudgetPlan plan = service.generatePlan(request());

        assertEquals("Front-load the emergency fund.", plan.getRationale());
        assertEquals(1, plan.getGoalContributions().size());
        assertEquals("sg_1", plan.getGoalContributions().get(0).getGoalId());
        assertEquals(250.0, plan.getGoalContributions().get(0).getSuggestedPerPeriodAmount());
        assertTrue(plan.getWarnings().isEmpty());
    }

    @Test
    void generatePlan_callsModelWithConfiguredModel() {
        when(client.generateJson(anyString(), anyString(), any(Schema.class)))
                .thenReturn("{\"rationale\":\"ok\",\"goalContributions\":[],\"warnings\":[]}");

        service.generatePlan(request());

        ArgumentCaptor<String> modelCaptor = ArgumentCaptor.forClass(String.class);
        verify(client).generateJson(modelCaptor.capture(), anyString(), any(Schema.class));
        assertEquals(MODEL, modelCaptor.getValue());
    }

    @Test
    void generatePlan_promptIncludesGoalAndExpenseDetails() {
        when(client.generateJson(anyString(), anyString(), any(Schema.class)))
                .thenReturn("{\"rationale\":\"ok\",\"goalContributions\":[],\"warnings\":[]}");

        service.generatePlan(request());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(client).generateJson(anyString(), promptCaptor.capture(), any(Schema.class));
        String prompt = promptCaptor.getValue();
        assertTrue(prompt.contains("sg_1"));
        assertTrue(prompt.contains("Emergency fund"));
        assertTrue(prompt.contains("Rent"));
        assertTrue(prompt.contains("March bonus"));
    }

    @Test
    void generatePlan_responseSchemaHasExpectedShape() {
        when(client.generateJson(anyString(), anyString(), any(Schema.class)))
                .thenReturn("{\"rationale\":\"ok\",\"goalContributions\":[],\"warnings\":[]}");

        service.generatePlan(request());

        ArgumentCaptor<Schema> schemaCaptor = ArgumentCaptor.forClass(Schema.class);
        verify(client).generateJson(anyString(), anyString(), schemaCaptor.capture());
        Schema schema = schemaCaptor.getValue();
        assertEquals(Type.Known.OBJECT, schema.type().orElseThrow().knownEnum());
        Map<String, Schema> properties = schema.properties().orElseThrow();
        assertTrue(properties.containsKey("rationale"));
        assertTrue(properties.containsKey("goalContributions"));
        assertTrue(properties.containsKey("warnings"));
    }

    @Test
    void generatePlan_malformedJson_throwsGenerativeAiException() {
        when(client.generateJson(anyString(), anyString(), any(Schema.class))).thenReturn("not json");

        BudgetPlanRequest request = request();
        assertThrows(GenerativeAiException.class, () -> service.generatePlan(request));
    }

    @Test
    void generatePlan_clientFailure_propagates() {
        when(client.generateJson(anyString(), anyString(), any(Schema.class)))
                .thenThrow(new GenerativeAiException("upstream down"));

        BudgetPlanRequest request = request();
        assertThrows(GenerativeAiException.class, () -> service.generatePlan(request));
    }
}
