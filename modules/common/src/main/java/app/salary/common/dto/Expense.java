package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import app.salary.common.constants.BudgetBucket;
import app.salary.common.constants.ExpenseCadence;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/**
 * An itemized expense within a user's household budget. Id is client-assigned,
 * same as {@link SavingsGoal}.
 */
@ExcludeFromCodeCoverage
@Setter
@Getter
@Schema(description = "An itemized expense within the user's household budget")
public class Expense {
    @Schema(description = "Client-assigned expense id", example = "exp_a1b2c3d4")
    private String id;

    @NotBlank
    @Schema(description = "Display name", example = "Rent")
    private String name;

    @NotNull
    @Positive
    @Schema(description = "Amount per cadence", example = "1800")
    private Double amount;

    @NotNull
    @Schema(description = "How often this expense recurs")
    private ExpenseCadence cadence;

    @NotNull
    @Schema(description = "50/30/20-style budget bucket")
    private BudgetBucket bucket;

    @Schema(description = "Day this is due (ISO-8601 yyyy-MM-dd), optional", example = "2025-08-01")
    private String dueDate;

    @Schema(description = "Free-form category label, optional", example = "Housing")
    private String category;
}
