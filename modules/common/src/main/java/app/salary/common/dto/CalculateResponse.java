package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@ExcludeFromCodeCoverage
@Schema(description = "Salary calculation result with detailed tax breakdown")
public class CalculateResponse {
    @Schema(description = "Gross pay per payment period", example = "100000.0")
    private Double grossPerCadence;

    @Schema(description = "Net take-home pay per payment period", example = "72556.15")
    private Double netPerCadence;

    @Schema(description = "Currency code (USD for US, GBP for UK)", example = "USD")
    private String currency;

    @Schema(description = "Itemized list of deductions and taxes")
    private List<LineItem> lineItems;

    @Schema(description = "Human-readable explanations of calculations")
    private List<Explanation> explanation;

    @Schema(description = "Unique calculation identifier", example = "c_a1b2c3d4")
    private String calculationId;

    @Schema(description = "Version of tax rules used", example = "US-2025.10.0")
    private String rulePackVersion;

    public Double getGrossPerCadence() { return grossPerCadence; }
    public void setGrossPerCadence(Double grossPerCadence) { this.grossPerCadence = grossPerCadence; }
    public Double getNetPerCadence() { return netPerCadence; }
    public void setNetPerCadence(Double netPerCadence) { this.netPerCadence = netPerCadence; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public List<LineItem> getLineItems() { return lineItems; }
    public void setLineItems(List<LineItem> lineItems) { this.lineItems = lineItems; }
    public List<Explanation> getExplanation() { return explanation; }
    public void setExplanation(List<Explanation> explanation) { this.explanation = explanation; }
    public String getCalculationId() { return calculationId; }
    public void setCalculationId(String calculationId) { this.calculationId = calculationId; }
    public String getRulePackVersion() { return rulePackVersion; }
    public void setRulePackVersion(String rulePackVersion) { this.rulePackVersion = rulePackVersion; }
}
