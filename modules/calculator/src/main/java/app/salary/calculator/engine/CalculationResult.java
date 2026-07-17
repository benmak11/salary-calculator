package app.salary.calculator.engine;

import app.salary.common.dto.Explanation;
import app.salary.common.dto.LineItem;
import app.salary.common.dto.LineItemCategory;
import app.salary.common.dto.SupplementalBreakdown;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class CalculationResult {
    private Double grossAnnual;
    private Double baseSalaryAnnual;
    private Double bonusAnnual = 0.0;
    private Double netAnnual;
    private String currency;
    private SupplementalBreakdown supplemental;
    private List<LineItem> lineItems = new ArrayList<>();
    private List<Explanation> explanations = new ArrayList<>();
    private String rulePackVersion;

    public void addLineItem(String name, Double amount) {
        lineItems.add(new LineItem(name, amount));
    }

    public void addLineItem(String name, Double amount, LineItemCategory category) {
        lineItems.add(new LineItem(name, amount, category));
    }

    public void addExplanation(String id, String text) {
        explanations.add(new Explanation(id, text));
    }
}
