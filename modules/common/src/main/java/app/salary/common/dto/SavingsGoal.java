package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import app.salary.common.constants.GoalType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

/**
 * A savings goal within a user's household budget. Ids are client-assigned
 * (stable identity for local list management) — the server stores whatever
 * the client sends and never validates it.
 */
@ExcludeFromCodeCoverage
@Setter
@Getter
@Schema(description = "A savings goal within the user's household budget")
public class SavingsGoal {
    @Schema(description = "Client-assigned goal id", example = "sg_a1b2c3d4")
    private String id;

    @NotNull
    @Schema(description = "Goal category")
    private GoalType type;

    @NotBlank
    @Schema(description = "Display name", example = "Emergency fund")
    private String name;

    @NotNull
    @PositiveOrZero
    @Schema(description = "Target amount to save", example = "10000")
    private Double targetAmount;

    @Schema(description = "Target date (ISO-8601 yyyy-MM-dd), optional", example = "2027-06-01")
    private String targetDate;

    @NotNull
    @Min(1)
    @Schema(description = "Priority rank; lower resolves funding contention first", example = "1")
    private Integer priority;

    @NotNull
    @PositiveOrZero
    @Schema(description = "Amount already saved toward this goal", example = "1500")
    private Double currentSaved = 0.0;
}
