package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Setter
@Getter
@ExcludeFromCodeCoverage
@Schema(description = "UK-specific calculation options (all optional with sensible defaults)")
public class CountryOptionsUK {
    @Schema(description = "UK tax code (defaults to 1257L for 2025/26)", example = "1257L")
    private String taxCode = "1257L";

    @Schema(description = "Scottish resident flag (affects tax rates)", example = "false")
    private Boolean scottishResident = false;

    @Schema(description = "National Insurance category", example = "A")
    private String niCategory = "A";

}
