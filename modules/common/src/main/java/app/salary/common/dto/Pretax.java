package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.ArrayList;
import java.util.List;

@ExcludeFromCodeCoverage
@Schema(description = "Pre-tax deductions configuration")
public class Pretax {
    @Min(0) @Max(1)
    @Schema(description = "Percentage-based pre-tax deduction (0-1)", example = "0.0")
    private Double percent = 0.0;

    @Min(0)
    @Schema(description = "Fixed pre-tax deduction amount (catch-all); use customDeductions for named items", example = "0.0")
    private Double fixed = 0.0;

    @Min(0)
    @Schema(description = "Health Savings Account contribution (US only)", example = "3850")
    private Double hsa = 0.0;

    @Min(0) @Max(1)
    @Schema(description = "Pension / 401(k) contribution percentage (0-1)", example = "0.05")
    private Double pensionPercent = 0.0;

    @Min(0)
    @Schema(description = "Annual medical insurance premium (US only — emitted as a named line item)", example = "2184.0")
    private Double medical = 0.0;

    @Min(0)
    @Schema(description = "Annual dental insurance premium (US only — emitted as a named line item)", example = "510.0")
    private Double dental = 0.0;

    @Min(0)
    @Schema(description = "Annual vision insurance premium (US only — emitted as a named line item)", example = "288.0")
    private Double vision = 0.0;

    @Valid
    @Schema(description = "Named custom pre-tax deductions; each is emitted as its own line item")
    private List<NamedDeduction> customDeductions = new ArrayList<>();

    public Double getPercent() { return percent; }
    public void setPercent(Double percent) { this.percent = percent; }
    public Double getFixed() { return fixed; }
    public void setFixed(Double fixed) { this.fixed = fixed; }
    public Double getHsa() { return hsa; }
    public void setHsa(Double hsa) { this.hsa = hsa; }
    public Double getPensionPercent() { return pensionPercent; }
    public void setPensionPercent(Double pensionPercent) { this.pensionPercent = pensionPercent; }
    public Double getMedical() { return medical; }
    public void setMedical(Double medical) { this.medical = medical; }
    public Double getDental() { return dental; }
    public void setDental(Double dental) { this.dental = dental; }
    public Double getVision() { return vision; }
    public void setVision(Double vision) { this.vision = vision; }
    public List<NamedDeduction> getCustomDeductions() { return customDeductions; }
    public void setCustomDeductions(List<NamedDeduction> customDeductions) {
        this.customDeductions = customDeductions != null ? customDeductions : new ArrayList<>();
    }
}
