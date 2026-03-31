package app.salary.api.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;

@ExcludeFromCodeCoverage
@Schema(description = "Employer 401(k) match details")
public class EmployerMatchResponse {
    @Schema(example = "4200.00")
    private Double annualMatchAmount;
    @Schema(example = "100.0", description = "Employer match percentage (e.g. 100 = dollar-for-dollar)")
    private Double matchPercent;
    @Schema(example = "6.0", description = "Employee contribution threshold that triggers the match")
    private Double matchThresholdPercent;
    @Schema(example = "Matched at 100% of your first 6% contribution.")
    private String matchDescription;

    public EmployerMatchResponse() {}

    public EmployerMatchResponse(Double annualMatchAmount, Double matchPercent,
                                 Double matchThresholdPercent, String matchDescription) {
        this.annualMatchAmount = annualMatchAmount;
        this.matchPercent = matchPercent;
        this.matchThresholdPercent = matchThresholdPercent;
        this.matchDescription = matchDescription;
    }

    public Double getAnnualMatchAmount() { return annualMatchAmount; }
    public void setAnnualMatchAmount(Double annualMatchAmount) { this.annualMatchAmount = annualMatchAmount; }
    public Double getMatchPercent() { return matchPercent; }
    public void setMatchPercent(Double matchPercent) { this.matchPercent = matchPercent; }
    public Double getMatchThresholdPercent() { return matchThresholdPercent; }
    public void setMatchThresholdPercent(Double matchThresholdPercent) { this.matchThresholdPercent = matchThresholdPercent; }
    public String getMatchDescription() { return matchDescription; }
    public void setMatchDescription(String matchDescription) { this.matchDescription = matchDescription; }
}
