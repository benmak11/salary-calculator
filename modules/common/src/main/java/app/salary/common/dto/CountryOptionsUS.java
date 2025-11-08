package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import app.salary.common.constants.FilingStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@ExcludeFromCodeCoverage
public class CountryOptionsUS {
    @NotNull
    private String state;

    @NotNull
    private FilingStatus filingStatus;

    @Min(0)
    private Integer allowances = 0;

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public FilingStatus getFilingStatus() { return filingStatus; }
    public void setFilingStatus(FilingStatus filingStatus) { this.filingStatus = filingStatus; }
    public Integer getAllowances() { return allowances; }
    public void setAllowances(Integer allowances) { this.allowances = allowances; }
}
