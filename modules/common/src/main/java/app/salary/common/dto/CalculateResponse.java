package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;

import java.util.List;

@ExcludeFromCodeCoverage
public class CalculateResponse {
    private Double grossPerCadence;
    private Double netPerCadence;
    private String currency;
    private List<LineItem> lineItems;
    private List<Explanation> explanation;
    private String calculationId;
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
