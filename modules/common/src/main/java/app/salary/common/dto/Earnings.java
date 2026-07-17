package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.*;

@Setter
@Getter
@ExcludeFromCodeCoverage
@Schema(description = "Earnings configuration; salary OR hourly is set, plus optional bonus / commission (both supplemental-taxed)")
public class Earnings {
    @Valid
    @Schema(description = "Salary earnings (mutually exclusive with hourly)")
    private Salary salary;

    @Valid
    @Schema(description = "Hourly earnings (mutually exclusive with salary)")
    private Hourly hourly;

    @Min(0)
    @Schema(description = "Bonus lump sum; taxed at supplemental flat rate (US only)", example = "0")
    private Double bonus = 0.0;

    @Schema(description = "Bonus payout date (ISO-8601 yyyy-MM-dd). Absent = paid in the requested tax year. "
            + "A one-time bonus dated outside the tax year is excluded from the calculation; "
            + "see bonusRecurring for the yearly-repeat rule.", example = "2026-03-15")
    private String bonusDate;

    @Schema(description = "When true the bonus repeats every year from its payout year onward, so it counts "
            + "toward the tax year whenever payout year <= tax year. Default false = one-time.", example = "false")
    private Boolean bonusRecurring = false;

    @Min(0)
    @Schema(description = "Annual commission; taxed at supplemental flat rate (US only)", example = "0")
    private Double commission = 0.0;

    @Min(0)
    @Schema(description = "RSU value vesting in the requested tax year (client-computed from grants at today's "
            + "price, or entered manually); taxed at supplemental flat rate (US only)", example = "0")
    private Double rsuVesting = 0.0;

}
