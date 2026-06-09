package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@ExcludeFromCodeCoverage
@Schema(description = "Country-specific calculation options")
public class CountryOptions {
    @JsonProperty("US")
    @Valid
    @Schema(description = "US-specific options (required for US calculations)")
    private CountryOptionsUS us;

    @JsonProperty("UK")
    @Valid
    @Schema(description = "UK-specific options (optional for UK calculations)")
    private CountryOptionsUK uk;
}
