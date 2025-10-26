package app.salary.calculator.engine;

import app.salary.common.dto.Explanation;
import app.salary.common.dto.LineItem;

import java.util.ArrayList;
import java.util.List;

public class CalculationResult {
    private Double grossAnnual;
    private Double netAnnual;
    private String currency;
    private List<LineItem> lineItems = new ArrayList<>();
    private List<Explanation> explanations = new ArrayList<>();
    private String rulePackVersion;

    public Double getGrossAnnual() { return grossAnnual; }
    public void setGrossAnnual(Double grossAnnual) { this.grossAnnual = grossAnnual; }
    public Double getNetAnnual() { return netAnnual; }
    public void setNetAnnual(Double netAnnual) { this.netAnnual = netAnnual; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public List<LineItem> getLineItems() { return lineItems; }
    public void setLineItems(List<LineItem> lineItems) { this.lineItems = lineItems; }
    public List<Explanation> getExplanations() { return explanations; }
    public void setExplanations(List<Explanation> explanations) { this.explanations = explanations; }
    public String getRulePackVersion() { return rulePackVersion; }
    public void setRulePackVersion(String rulePackVersion) { this.rulePackVersion = rulePackVersion; }

    public void addLineItem(String name, Double amount) { lineItems.add(new LineItem(name, amount)); }

    public void addExplanation(String id, String text) { explanations.add(new Explanation(id, text)); }
}
