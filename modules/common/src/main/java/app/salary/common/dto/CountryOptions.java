package app.salary.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;

public class CountryOptions {
    @JsonProperty("US")
    @Valid
    private CountryOptionsUS us;

    @JsonProperty("UK")
    @Valid
    private CountryOptionsUK uk;

    public CountryOptionsUS getUs() { return us; }
    public void setUs(CountryOptionsUS us) { this.us = us; }
    public CountryOptionsUK getUk() { return uk; }
    public void setUk(CountryOptionsUK uk) { this.uk = uk; }
}
