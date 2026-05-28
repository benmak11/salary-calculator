package app.salary.api.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;

@ExcludeFromCodeCoverage
@Schema(description = "A single enrolled benefit plan")
public class BenefitPlan {
    @Schema(example = "MEDICAL")
    private String type;
    @Schema(example = "PPO Platinum Plus")
    private String planName;
    @Schema(example = "Anthem")
    private String carrier;
    @Schema(example = "Family")
    private String tier;
    @Schema(example = "182.00")
    private Double monthlyPremium;
    @Schema(example = "PRE_TAX")
    private String taxTreatment;

    public BenefitPlan() {}

    public BenefitPlan(String type, String planName, String carrier,
                       String tier, Double monthlyPremium, String taxTreatment) {
        this.type = type;
        this.planName = planName;
        this.carrier = carrier;
        this.tier = tier;
        this.monthlyPremium = monthlyPremium;
        this.taxTreatment = taxTreatment;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }
    public String getCarrier() { return carrier; }
    public void setCarrier(String carrier) { this.carrier = carrier; }
    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }
    public Double getMonthlyPremium() { return monthlyPremium; }
    public void setMonthlyPremium(Double monthlyPremium) { this.monthlyPremium = monthlyPremium; }
    public String getTaxTreatment() { return taxTreatment; }
    public void setTaxTreatment(String taxTreatment) { this.taxTreatment = taxTreatment; }
}
