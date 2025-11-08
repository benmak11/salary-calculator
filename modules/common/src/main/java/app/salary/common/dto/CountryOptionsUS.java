package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import app.salary.common.constants.FilingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@ExcludeFromCodeCoverage
@Schema(description = "US-specific calculation options")
public class CountryOptionsUS {
    @NotNull
    @Schema(description = "US state code (e.g., CA, NY, TX)", example = "CA", required = true)
    private String state;

    @NotNull
    @Schema(description = "Tax filing status: SINGLE or MARRIED", example = "SINGLE", required = true)
    private FilingStatus filingStatus;

    @Min(0)
    @Schema(description = "Number of tax allowances", example = "2")
    private Integer allowances = 0;

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public FilingStatus getFilingStatus() { return filingStatus; }
    public void setFilingStatus(FilingStatus filingStatus) { this.filingStatus = filingStatus; }
    public Integer getAllowances() { return allowances; }
    public void setAllowances(Integer allowances) { this.allowances = allowances; }
}
