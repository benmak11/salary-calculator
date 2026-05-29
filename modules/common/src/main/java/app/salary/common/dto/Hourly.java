package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@ExcludeFromCodeCoverage
@Schema(description = "Hourly earnings line; hours are per pay period")
public class Hourly {
    @NotNull
    @Min(0)
    @Schema(description = "Hourly rate", example = "25.0")
    private Double rate;

    @Min(0)
    @Schema(description = "Regular hours per pay period", example = "80")
    private Double regularHours = 0.0;

    @Min(0)
    @Schema(description = "Overtime hours per pay period (paid at rate * overtimeMultiplier)", example = "0")
    private Double overtimeHours = 0.0;

    @Min(0)
    @Schema(description = "Overtime pay multiplier", example = "1.5")
    private Double overtimeMultiplier = 1.5;

    @Min(0)
    @Schema(description = "Double-time hours per pay period (paid at rate * doubleTimeMultiplier)", example = "0")
    private Double doubleTimeHours = 0.0;

    @Min(0)
    @Schema(description = "Double-time pay multiplier", example = "2.0")
    private Double doubleTimeMultiplier = 2.0;

    public Double getRate() { return rate; }
    public void setRate(Double rate) { this.rate = rate; }
    public Double getRegularHours() { return regularHours; }
    public void setRegularHours(Double regularHours) { this.regularHours = regularHours; }
    public Double getOvertimeHours() { return overtimeHours; }
    public void setOvertimeHours(Double overtimeHours) { this.overtimeHours = overtimeHours; }
    public Double getOvertimeMultiplier() { return overtimeMultiplier; }
    public void setOvertimeMultiplier(Double overtimeMultiplier) { this.overtimeMultiplier = overtimeMultiplier; }
    public Double getDoubleTimeHours() { return doubleTimeHours; }
    public void setDoubleTimeHours(Double doubleTimeHours) { this.doubleTimeHours = doubleTimeHours; }
    public Double getDoubleTimeMultiplier() { return doubleTimeMultiplier; }
    public void setDoubleTimeMultiplier(Double doubleTimeMultiplier) { this.doubleTimeMultiplier = doubleTimeMultiplier; }
}
