package app.salary.api.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;

@ExcludeFromCodeCoverage
@Schema(description = "Employee deferral details for a single retirement account tier")
public class RetirementTierResponse {
    @Schema(example = "8.0")
    private Double employeePercent;
    @Schema(example = "25.0")
    private Double maxPercent;

    public RetirementTierResponse() {}

    public RetirementTierResponse(Double employeePercent, Double maxPercent) {
        this.employeePercent = employeePercent;
        this.maxPercent = maxPercent;
    }

    public Double getEmployeePercent() { return employeePercent; }
    public void setEmployeePercent(Double employeePercent) { this.employeePercent = employeePercent; }
    public Double getMaxPercent() { return maxPercent; }
    public void setMaxPercent(Double maxPercent) { this.maxPercent = maxPercent; }
}
