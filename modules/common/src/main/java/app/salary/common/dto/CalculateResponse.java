package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@ExcludeFromCodeCoverage
@Schema(description = "Salary calculation result with detailed tax breakdown")
@Setter
@Getter
public class CalculateResponse {
    @Schema(description = "Regular gross pay per payment period (salary/overtime/double-time only; "
            + "bonus, commission, and RSU vesting are lump-sum and excluded — see `supplemental`)",
            example = "100000.0")
    private Double grossPerCadence;

    @Schema(description = "Base salary component per payment period", example = "92307.69")
    private Double baseSalaryPerCadence;

    @Schema(description = "Always 0.0 — bonus/commission/RSU are lump-sum, not per-cadence; "
            + "see `supplemental` for the annual breakdown", example = "0.0")
    private Double bonusPerCadence;

    @Schema(description = "Net take-home pay per payment period", example = "72556.15")
    private Double netPerCadence;

    @Schema(description = "Currency code (USD for US, GBP for UK)", example = "USD")
    private String currency;

    @Schema(description = "Annual tax slice for supplemental income (bonus/commission/RSU); "
            + "null when there is no supplemental income")
    private SupplementalBreakdown supplemental;

    @Schema(description = "Itemized list of deductions and taxes")
    private List<LineItem> lineItems;

    @Schema(description = "Human-readable explanations of calculations")
    private List<Explanation> explanation;

    @Schema(description = "Unique calculation identifier", example = "c_a1b2c3d4")
    private String calculationId;

    @Schema(description = "Version of tax rules used", example = "US-2025.10.0")
    private String rulePackVersion;

}
