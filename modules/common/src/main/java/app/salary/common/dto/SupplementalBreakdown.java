package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * Server-truth tax slice for supplemental income (bonus + commission + RSU vesting).
 *
 * All figures are ANNUAL dollars — supplemental income is lump-sum-shaped, so unlike the
 * rest of {@link CalculateResponse} it is not divided by pay cadence. The taxes here are
 * attribution slices of the response totals (supplemental wages stack on top of regular
 * wages for the Social Security cap and Additional Medicare threshold), not extra tax.
 */
@ExcludeFromCodeCoverage
@Setter
@Getter
@Schema(description = "Annual tax breakdown attributable to supplemental income (bonus/commission/RSU). "
        + "Present only when supplemental income > 0. Slices the response totals; not additive to them.")
public class SupplementalBreakdown {
    @Schema(description = "Bonus included in this tax year (annual)", example = "10000.0")
    private Double bonusGross = 0.0;

    @Schema(description = "Commission (annual)", example = "0.0")
    private Double commissionGross = 0.0;

    @Schema(description = "RSU value vesting this tax year (annual)", example = "18750.0")
    private Double rsuGross = 0.0;

    @Schema(description = "Federal withholding on supplemental wages at the flat supplemental rate (annual)",
            example = "6325.0")
    private Double federalTax = 0.0;

    @Schema(description = "Social Security attributable to supplemental wages — regular wages fill the "
            + "SS wage base first (annual)", example = "1782.5")
    private Double socialSecurity = 0.0;

    @Schema(description = "Medicare attributable to supplemental wages, including the Additional Medicare "
            + "portion above the threshold (annual)", example = "416.88")
    private Double medicare = 0.0;

    @Schema(description = "Supplemental income net of the taxes above (annual)", example = "20225.62")
    private Double net = 0.0;
}
