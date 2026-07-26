package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * AI-generated budget strategy: a plain-language rationale plus a suggested
 * per-period contribution rate for each goal. Field names/shape mirror the
 * JSON schema {@code BudgetPlanService} hands to Gemini as structured output
 * — this class doubles as the deserialization target for the model's response.
 */
@ExcludeFromCodeCoverage
@Setter
@Getter
@Schema(description = "AI-generated budget strategy")
public class BudgetPlan {
    @Schema(description = "Plain-language explanation of how the plan was built")
    private String rationale;

    @Schema(description = "Suggested per-period contribution for each goal in the request")
    private List<GoalContribution> goalContributions = new ArrayList<>();

    @Schema(description = "Notable risks, e.g. a dated goal that won't be met at the suggested rate")
    private List<String> warnings = new ArrayList<>();
}
