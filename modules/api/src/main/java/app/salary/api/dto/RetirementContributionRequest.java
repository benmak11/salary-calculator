package app.salary.api.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

@ExcludeFromCodeCoverage
@Schema(description = "Request to update an employee retirement contribution rate")
public class RetirementContributionRequest {
    @NotBlank
    @Schema(description = "Account type: TRADITIONAL_401K or ROTH_401K", example = "TRADITIONAL_401K")
    private String type;

    @DecimalMin("0.0")
    @DecimalMax("25.0")
    @Schema(description = "New contribution percentage (0–25)", example = "9.0")
    private Double employeePercent;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Double getEmployeePercent() { return employeePercent; }
    public void setEmployeePercent(Double employeePercent) { this.employeePercent = employeePercent; }
}
