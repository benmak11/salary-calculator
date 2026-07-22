package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * A signed-in user's single household budget — savings goals + itemized
 * expenses. One per user (unlike RSU grants, which are a list of independently
 * addressable items); {@code PUT /v1/budget} replaces the whole object.
 */
@ExcludeFromCodeCoverage
@Setter
@Getter
@Schema(description = "A signed-in user's single household budget: savings goals + itemized expenses")
public class Budget {
    @Valid
    @Schema(description = "Savings goals; the priority field on each resolves funding contention")
    private List<SavingsGoal> goals = new ArrayList<>();

    @Valid
    @Schema(description = "Itemized expenses")
    private List<Expense> expenses = new ArrayList<>();
}
