package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import app.salary.common.constants.FilingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Setter
@Getter
@ExcludeFromCodeCoverage
@Schema(description = "US-specific calculation options")
public class CountryOptionsUS {
    @NotNull
    @Schema(description = "US state code (e.g., CA, NY, TX)", example = "CA")
    private String state;

    @NotNull
    @Schema(description = "Tax filing status: SINGLE, MARRIED, or HEAD_OF_HOUSEHOLD", example = "SINGLE")
    private FilingStatus filingStatus;

    @Min(0)
    @Schema(description = "Number of tax allowances (legacy / pre-2020 W-4)", example = "2")
    private Integer allowances = 0;

    @Valid
    @Schema(description = "Modern W-4 fields (2020+). Optional; engine ignores in Phase 1.")
    private W4 w4;

}
