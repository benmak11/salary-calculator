package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@ExcludeFromCodeCoverage
@Schema(description = "Pre-tax deductions configuration")
public class Pretax {
    @Min(0) @Max(1)
    @Schema(description = "Percentage-based pre-tax deduction (0-1)", example = "0.0")
    private Double percent = 0.0;

    @Min(0)
    @Schema(description = "Fixed pre-tax deduction amount", example = "0.0")
    private Double fixed = 0.0;

    @Min(0)
    @Schema(description = "Health Savings Account contribution (US only)", example = "3850")
    private Double hsa = 0.0;

    @Min(0) @Max(1)
    @Schema(description = "Pension contribution percentage (0-1)", example = "0.05")
    private Double pensionPercent = 0.0;

    public Double getPercent() { return percent; }
    public void setPercent(Double percent) { this.percent = percent; }
    public Double getFixed() { return fixed; }
    public void setFixed(Double fixed) { this.fixed = fixed; }
    public Double getHsa() { return hsa; }
    public void setHsa(Double hsa) { this.hsa = hsa; }
    public Double getPensionPercent() { return pensionPercent; }
    public void setPensionPercent(Double pensionPercent) { this.pensionPercent = pensionPercent; }
}
