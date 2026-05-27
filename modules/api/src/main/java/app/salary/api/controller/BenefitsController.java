package app.salary.api.controller;

import app.salary.api.dto.*;
import app.salary.api.service.CalculationStore;
import app.salary.api.validation.RequestValidator;
import app.salary.common.dto.CalculateResponse;
import app.salary.common.dto.LineItem;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BenefitsController {

    private final CalculationStore calculationStore;
    private final RequestValidator validator;

    public BenefitsController(CalculationStore calculationStore, RequestValidator validator) {
        this.calculationStore = calculationStore;
        this.validator = validator;
    }

    public void register(Javalin app) {
        app.get( "/v1/benefits/plans",                              this::getBenefitPlans);
        app.get( "/v1/benefits/employer-match",                     this::getEmployerMatch);
        app.get( "/v1/benefits/impact-analysis/{calculationId}",    this::getImpactAnalysis);
        app.get( "/v1/benefits/retirement/contributions",           this::getRetirementContributions);
        app.put( "/v1/benefits/retirement/contribution",            this::updateRetirementContribution);
        app.get( "/v1/benefits/enrollment/window",                  this::getEnrollmentWindow);
    }

    private void getBenefitPlans(Context ctx) {
        // TODO: Replace with employer-specific data fetched from a benefits provider.
        List<BenefitPlan> healthPlans = List.of(
                new BenefitPlan("MEDICAL", "PPO Platinum Plus", "Anthem",       "Family",        182.00, "PRE_TAX"),
                new BenefitPlan("DENTAL",  "Delta Premier",     "Delta Dental", "Family",         42.50, "PRE_TAX"),
                new BenefitPlan("VISION",  "VSP Vision Care",   "VSP",          "Employee + 1",   24.00, "PRE_TAX")
        );
        ctx.json(new BenefitPlansResponse(2024, "ACTIVE", healthPlans));
    }

    private void getEmployerMatch(Context ctx) {
        // TODO: Source from employer payroll configuration.
        ctx.json(new EmployerMatchResponse(
                4200.00, 100.0, 6.0,
                "Matched at 100% of your first 6% contribution."
        ));
    }

    private void getImpactAnalysis(Context ctx) {
        String calculationId = ctx.pathParam("calculationId");
        Optional<CalculateResponse> stored = calculationStore.find(calculationId);
        if (stored.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }

        CalculateResponse calc = stored.get();
        double netAnnual   = calc.getNetPerCadence();

        double totalTaxes    = sumLineItems(calc.getLineItems(), "Income Tax", "Medicare",
                "Social Security", "FICA");
        double totalBenefits = sumLineItems(calc.getLineItems(), "Medical", "Dental",
                "Vision", "401(k)", "HSA");
        double takeHome      = netAnnual;

        double total = takeHome + totalTaxes + totalBenefits;
        List<ImpactAnalysisEntry> breakdown = new ArrayList<>();
        if (total > 0) {
            breakdown.add(new ImpactAnalysisEntry("Take Home", round(takeHome / total * 100)));
            breakdown.add(new ImpactAnalysisEntry("Taxes",     round(totalTaxes / total * 100)));
            breakdown.add(new ImpactAnalysisEntry("Benefits",  round(totalBenefits / total * 100)));
        }

        ctx.json(new ImpactAnalysisResponse(calculationId, breakdown));
    }

    private void getRetirementContributions(Context ctx) {
        // TODO: Source from payroll system.
        RetirementTierResponse traditional = new RetirementTierResponse(8.0, 25.0);
        RetirementTierResponse roth        = new RetirementTierResponse(4.0, 25.0);
        ctx.json(new RetirementContributionsResponse(traditional, roth, 12.0));
    }

    private void updateRetirementContribution(Context ctx) {
        RetirementContributionRequest request = ctx.bodyAsClass(RetirementContributionRequest.class);
        validator.validate(request);

        // TODO: Persist to payroll system and trigger next-payroll recalculation.
        if (!"TRADITIONAL_401K".equals(request.getType()) && !"ROTH_401K".equals(request.getType())) {
            ctx.status(HttpStatus.BAD_REQUEST);
            return;
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void getEnrollmentWindow(Context ctx) {
        // TODO: Source from HR system. Currently returns a hardcoded open window.
        ctx.json(new EnrollmentWindowResponse(
                "OPEN", 14,
                "2024-11-01", "2024-11-30", "2024-12-01"
        ));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private double sumLineItems(List<LineItem> items, String... keywords) {
        if (items == null) return 0.0;
        double total = 0.0;
        for (LineItem item : items) {
            for (String kw : keywords) {
                if (item.getName() != null && item.getName().contains(kw)) {
                    total += item.getAmount() != null ? item.getAmount() : 0.0;
                    break;
                }
            }
        }
        return total;
    }

    private double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
