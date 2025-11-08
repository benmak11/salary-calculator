package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@ExcludeFromCodeCoverage
public class Pretax {
    @Min(0) @Max(1)
    private Double percent = 0.0;

    @Min(0)
    private Double fixed = 0.0;

    @Min(0)
    private Double hsa = 0.0;

    @Min(0) @Max(1)
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
