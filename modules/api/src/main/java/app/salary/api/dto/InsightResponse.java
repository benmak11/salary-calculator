package app.salary.api.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;

@ExcludeFromCodeCoverage
@Schema(description = "Personalised financial insight derived from a salary calculation")
public class InsightResponse {
    @Schema(example = "RETIREMENT_OPTIMIZATION")
    private String type;
    @Schema(example = "Smart Saving Insight")
    private String headline;
    @Schema(example = "78.50", description = "Monthly federal + state tax savings from a 1% 401(k) increase")
    private Double monthlyTaxSavings;
    @Schema(example = "1884.00", description = "Annual retirement benefit including employer match")
    private Double annualRetirementBenefit;
    @Schema(example = "7.0", description = "Recommended total contribution percentage")
    private Double recommendedContributionPercent;
    @Schema(example = "By increasing your 401(k) contribution by just 1%...")
    private String body;
    @Schema(example = "Optimize 401k")
    private String ctaLabel;

    public InsightResponse() {}

    public InsightResponse(String type, String headline, Double monthlyTaxSavings,
                           Double annualRetirementBenefit, Double recommendedContributionPercent,
                           String body, String ctaLabel) {
        this.type = type;
        this.headline = headline;
        this.monthlyTaxSavings = monthlyTaxSavings;
        this.annualRetirementBenefit = annualRetirementBenefit;
        this.recommendedContributionPercent = recommendedContributionPercent;
        this.body = body;
        this.ctaLabel = ctaLabel;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getHeadline() { return headline; }
    public void setHeadline(String headline) { this.headline = headline; }
    public Double getMonthlyTaxSavings() { return monthlyTaxSavings; }
    public void setMonthlyTaxSavings(Double monthlyTaxSavings) { this.monthlyTaxSavings = monthlyTaxSavings; }
    public Double getAnnualRetirementBenefit() { return annualRetirementBenefit; }
    public void setAnnualRetirementBenefit(Double annualRetirementBenefit) { this.annualRetirementBenefit = annualRetirementBenefit; }
    public Double getRecommendedContributionPercent() { return recommendedContributionPercent; }
    public void setRecommendedContributionPercent(Double recommendedContributionPercent) { this.recommendedContributionPercent = recommendedContributionPercent; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getCtaLabel() { return ctaLabel; }
    public void setCtaLabel(String ctaLabel) { this.ctaLabel = ctaLabel; }
}
