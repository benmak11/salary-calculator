package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;

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

    public CountryOptionsUS getUs() { return us; }
    public void setUs(CountryOptionsUS us) { this.us = us; }
    public CountryOptionsUK getUk() { return uk; }
    public void setUk(CountryOptionsUK uk) { this.uk = uk; }
}
