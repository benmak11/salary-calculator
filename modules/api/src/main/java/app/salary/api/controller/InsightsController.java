package app.salary.api.controller;

import app.salary.api.dto.InsightResponse;
import app.salary.api.service.CalculationStore;
import app.salary.common.dto.CalculateResponse;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.util.Optional;

public class InsightsController {

    /** Assumed employer match rate for insight calculations (100% = dollar-for-dollar). */
    private static final double EMPLOYER_MATCH_RATE     = 1.0;
    /** Contribution increase modelled in the insight (1%). */
    private static final double MODELLED_INCREASE_PCT   = 0.01;
    /** Recommended total contribution percentage shown in the CTA. */
    private static final double RECOMMENDED_CONTRIBUTION = 7.0;

    private final CalculationStore calculationStore;

    public InsightsController(CalculationStore calculationStore) {
        this.calculationStore = calculationStore;
    }

    public void register(RoutesConfig routes) {
        routes.get("/v1/insights/{calculationId}", this::getInsight);
    }

    private void getInsight(Context ctx) {
        String calculationId = ctx.pathParam("calculationId");
        Optional<CalculateResponse> stored = calculationStore.find(calculationId);
        if (stored.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }

        CalculateResponse calc = stored.get();
        double annualGross = calc.getGrossPerCadence(); // ANNUAL cadence = full year amount

        double effectiveTaxRate = deriveTaxRate(calc);
        double marginalRate     = Math.min(effectiveTaxRate + 0.05, 0.37);

        double annualContributionIncrease = annualGross * MODELLED_INCREASE_PCT;
        double annualTaxSavings           = annualContributionIncrease * marginalRate;
        double monthlyTaxSavings          = round(annualTaxSavings / 12);

        double annualRetirementBenefit = round(annualContributionIncrease * (1 + EMPLOYER_MATCH_RATE));

        String body = String.format(
                "By increasing your 401(k) contribution by just 1%% you could reduce your " +
                "taxable income by $%.2f per month and secure an additional $%.2f for " +
                "your retirement annually (including employer match).",
                monthlyTaxSavings, annualRetirementBenefit
        );

        InsightResponse insight = new InsightResponse(
                "RETIREMENT_OPTIMIZATION",
                "Smart Saving Insight",
                monthlyTaxSavings,
                annualRetirementBenefit,
                RECOMMENDED_CONTRIBUTION,
                body,
                "Optimize 401k"
        );

        ctx.json(insight);
    }

    private double deriveTaxRate(CalculateResponse calc) {
        if (calc.getLineItems() == null || calc.getGrossPerCadence() == null
                || calc.getGrossPerCadence() == 0) {
            return 0.22;
        }
        double totalTaxes = calc.getLineItems().stream()
                .filter(item -> item.getName() != null &&
                        (item.getName().contains("Income Tax") || item.getName().contains("Medicare")
                                || item.getName().contains("Social Security") || item.getName().contains("FICA")))
                .mapToDouble(item -> item.getAmount() != null ? item.getAmount() : 0.0)
                .sum();
        return totalTaxes / calc.getGrossPerCadence();
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
