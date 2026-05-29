package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

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
    @Schema(description = "Annual bonus; taxed at supplemental flat rate (US only)", example = "0")
    private Double bonus = 0.0;

    @Min(0)
    @Schema(description = "Annual commission; taxed at supplemental flat rate (US only)", example = "0")
    private Double commission = 0.0;

    public Salary getSalary() { return salary; }
    public void setSalary(Salary salary) { this.salary = salary; }
    public Hourly getHourly() { return hourly; }
    public void setHourly(Hourly hourly) { this.hourly = hourly; }
    public Double getBonus() { return bonus; }
    public void setBonus(Double bonus) { this.bonus = bonus; }
    public Double getCommission() { return commission; }
    public void setCommission(Double commission) { this.commission = commission; }
}
