package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * The AI-suggested per-period contribution rate for a single goal. This is
 * the "strategy" half of the plan — the on-device engine (BudgetEngine on
 * iOS) is what actually simulates and verifies these numbers against the
 * paycheck timeline.
 */
@ExcludeFromCodeCoverage
@Setter
@Getter
@Schema(description = "Suggested per-pay-period contribution toward one goal")
public class GoalContribution {
    @Schema(description = "Matches a SavingsGoal.id from the request budget")
    private String goalId;

    @Schema(description = "Suggested dollar amount to contribute per pay period", example = "250")
    private Double suggestedPerPeriodAmount;
}
