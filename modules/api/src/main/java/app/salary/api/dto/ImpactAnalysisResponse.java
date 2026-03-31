package app.salary.api.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@ExcludeFromCodeCoverage
@Schema(description = "Gross pay impact analysis breakdown for a completed calculation")
public class ImpactAnalysisResponse {
    @Schema(example = "c_abc123")
    private String calculationId;
    private List<ImpactAnalysisEntry> breakdown;

    public ImpactAnalysisResponse() {}

    public ImpactAnalysisResponse(String calculationId, List<ImpactAnalysisEntry> breakdown) {
        this.calculationId = calculationId;
        this.breakdown = breakdown;
    }

    public String getCalculationId() { return calculationId; }
    public void setCalculationId(String calculationId) { this.calculationId = calculationId; }
    public List<ImpactAnalysisEntry> getBreakdown() { return breakdown; }
    public void setBreakdown(List<ImpactAnalysisEntry> breakdown) { this.breakdown = breakdown; }
}
