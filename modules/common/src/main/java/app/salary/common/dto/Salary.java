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
@Schema(description = "Salary earnings line")
public class Salary {
    public enum Basis {
        PER_YEAR,
        PER_PERIOD
    }

    @NotNull
    @Min(0)
    @Schema(description = "Salary amount", example = "200000")
    private Double amount;

    @NotNull
    @Schema(description = "Whether amount is per year or per pay period", example = "PER_YEAR")
    private Basis basis = Basis.PER_YEAR;

}
