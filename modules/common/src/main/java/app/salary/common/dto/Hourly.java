package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
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

}
