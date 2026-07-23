package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import app.salary.common.constants.PayCadence;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Input for {@code POST /v1/budget/plan}. The budget being planned is sent
 * wholesale (not read from storage) so anonymous/not-yet-saved budgets can
 * still get a plan preview, matching the calculator's public-by-default
 * philosophy.
 */
@ExcludeFromCodeCoverage
@Setter
@Getter
@Schema(description = "Input for generating a paycheck-by-paycheck budget plan")
public class BudgetPlanRequest {
    @NotNull
    @Valid
    @Schema(description = "Goals + expenses to plan around")
    private Budget budget;

    @NotNull
    @Schema(description = "Pay cadence the plan should simulate against")
    private PayCadence payFrequency;

    @NotNull
    @Positive
    @Schema(description = "Net take-home per pay period, from the latest calculation", example = "2400")
    private Double netIncomePerPeriod;

    @Valid
    @Schema(description = "Dated windfalls (bonus/RSU vests) within the planning horizon")
    private List<Windfall> windfalls = new ArrayList<>();
}
