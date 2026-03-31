package app.salary.api.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;

@ExcludeFromCodeCoverage
@Schema(description = "A single segment in the gross pay impact breakdown")
public class ImpactAnalysisEntry {
    @Schema(example = "Take Home")
    private String label;
    @Schema(example = "68.0")
    private Double percent;

    public ImpactAnalysisEntry() {}

    public ImpactAnalysisEntry(String label, Double percent) {
        this.label = label;
        this.percent = percent;
    }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Double getPercent() { return percent; }
    public void setPercent(Double percent) { this.percent = percent; }
}
