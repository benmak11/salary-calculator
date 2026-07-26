package app.salary.api.service;

import app.salary.api.client.GenerativeAiClient;
import app.salary.api.client.GenerativeAiException;
import app.salary.common.dto.BudgetPlan;
import app.salary.common.dto.BudgetPlanRequest;
import app.salary.common.dto.Expense;
import app.salary.common.dto.SavingsGoal;
import app.salary.common.dto.Windfall;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;

import java.util.Map;

/**
 * Turns a household's goals/expenses into an AI-suggested contribution
 * strategy. This is the "strategy" half of the locked design — the on-device
 * BudgetEngine (iOS) is what simulates the paycheck timeline and verifies
 * these numbers, both as a fallback when this service is unavailable and as
 * a sanity check on what comes back from here.
 *
 * Never logs the prompt or the model's raw response — both carry salary
 * amounts.
 */
public class BudgetPlanService {

    private static final Schema RESPONSE_SCHEMA = Schema.builder()
            .type(Type.Known.OBJECT)
            .properties(Map.of(
                    "rationale", Schema.builder().type(Type.Known.STRING).build(),
                    "goalContributions", Schema.builder()
                            .type(Type.Known.ARRAY)
                            .items(Schema.builder()
                                    .type(Type.Known.OBJECT)
                                    .properties(Map.of(
                                            "goalId", Schema.builder().type(Type.Known.STRING).build(),
                                            "suggestedPerPeriodAmount", Schema.builder().type(Type.Known.NUMBER).build()
                                    ))
                                    .required("goalId", "suggestedPerPeriodAmount")
                                    .build())
                            .build(),
                    "warnings", Schema.builder()
                            .type(Type.Known.ARRAY)
                            .items(Schema.builder().type(Type.Known.STRING).build())
                            .build()
            ))
            .required("rationale", "goalContributions", "warnings")
            .build();

    private final GenerativeAiClient client;
    private final ObjectMapper objectMapper;
    private final String model;

    public BudgetPlanService(GenerativeAiClient client, ObjectMapper objectMapper, String model) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.model = model;
    }

    public BudgetPlan generatePlan(BudgetPlanRequest request) {
        String prompt = buildPrompt(request);
        String json = client.generateJson(model, prompt, RESPONSE_SCHEMA);
        try {
            return objectMapper.readValue(json, BudgetPlan.class);
        } catch (JsonProcessingException e) {
            throw new GenerativeAiException("Malformed plan JSON from " + model, e);
        }
    }

    private String buildPrompt(BudgetPlanRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a budgeting assistant. Given a household's savings goals, itemized ")
                .append("expenses, pay cadence, and any upcoming windfalls, suggest a per-pay-period ")
                .append("dollar contribution for EVERY goal listed below so that higher-priority goals ")
                .append("(lower priority number) are funded first, contributions never exceed what is ")
                .append("left over after needs and wants, and windfalls are used to accelerate ")
                .append("still-unmet goals. If a dated goal can't reasonably be met by its target date ")
                .append("at the suggested rate, add a plain-language warning about it. Keep the rationale ")
                .append("to 2-3 sentences. Respond with structured JSON only.\n\n");

        sb.append("Pay cadence: ").append(request.getPayFrequency())
                .append(" (").append(request.getPayFrequency().getPeriodsPerYear()).append(" periods/year)\n");
        sb.append("Net take-home per period: $").append(fmt(request.getNetIncomePerPeriod())).append("\n");

        sb.append("\nExpenses:\n");
        for (Expense e : request.getBudget().getExpenses()) {
            sb.append("- ").append(e.getName())
                    .append(": $").append(fmt(e.getAmount()))
                    .append(" ").append(e.getCadence())
                    .append(", bucket=").append(e.getBucket());
            if (e.getDueDate() != null) {
                sb.append(", due=").append(e.getDueDate());
            }
            sb.append("\n");
        }

        sb.append("\nGoals (id, priority 1=highest, target, already saved, optional target date):\n");
        for (SavingsGoal g : request.getBudget().getGoals()) {
            sb.append("- id=").append(g.getId())
                    .append(", \"").append(g.getName()).append("\"")
                    .append(", priority=").append(g.getPriority())
                    .append(", target=$").append(fmt(g.getTargetAmount()))
                    .append(", saved=$").append(fmt(g.getCurrentSaved()));
            if (g.getTargetDate() != null) {
                sb.append(", targetDate=").append(g.getTargetDate());
            }
            sb.append("\n");
        }

        if (!request.getWindfalls().isEmpty()) {
            sb.append("\nUpcoming windfalls (net of tax):\n");
            for (Windfall w : request.getWindfalls()) {
                sb.append("- ").append(w.getLabel())
                        .append(": $").append(fmt(w.getNetAmount()))
                        .append(" on ").append(w.getDate())
                        .append("\n");
            }
        }

        sb.append("\nReturn one goalContributions entry for every goal id listed above.");
        return sb.toString();
    }

    private static String fmt(Double value) {
        return String.format("%.2f", value);
    }
}
