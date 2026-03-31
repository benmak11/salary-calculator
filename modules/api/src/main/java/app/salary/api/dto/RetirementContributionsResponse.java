package app.salary.api.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;

@ExcludeFromCodeCoverage
@Schema(description = "Current employee retirement deferral rates")
public class RetirementContributionsResponse {
    private RetirementTierResponse traditional401k;
    private RetirementTierResponse roth401k;
    @Schema(example = "12.0")
    private Double totalContributionPercent;

    public RetirementContributionsResponse() {}

    public RetirementContributionsResponse(RetirementTierResponse traditional401k,
                                           RetirementTierResponse roth401k,
                                           Double totalContributionPercent) {
        this.traditional401k = traditional401k;
        this.roth401k = roth401k;
        this.totalContributionPercent = totalContributionPercent;
    }

    public RetirementTierResponse getTraditional401k() { return traditional401k; }
    public void setTraditional401k(RetirementTierResponse traditional401k) { this.traditional401k = traditional401k; }
    public RetirementTierResponse getRoth401k() { return roth401k; }
    public void setRoth401k(RetirementTierResponse roth401k) { this.roth401k = roth401k; }
    public Double getTotalContributionPercent() { return totalContributionPercent; }
    public void setTotalContributionPercent(Double totalContributionPercent) { this.totalContributionPercent = totalContributionPercent; }
}
