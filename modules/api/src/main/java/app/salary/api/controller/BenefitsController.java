package app.salary.api.controller;

import app.salary.api.dto.*;
import app.salary.api.service.CalculationStore;
import app.salary.common.dto.CalculateResponse;
import app.salary.common.dto.LineItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/v1/benefits")
@Tag(name = "Benefits", description = "Employee benefits and contribution endpoints")
public class BenefitsController {

    private final CalculationStore calculationStore;

    public BenefitsController(CalculationStore calculationStore) {
        this.calculationStore = calculationStore;
    }

    // ── GET /v1/benefits/plans ─────────────────────────────────────────────────

    @GetMapping("/plans")
    @Operation(summary = "Return the employee's current enrolled benefit plans")
    public ResponseEntity<BenefitPlansResponse> getBenefitPlans() {
        // TODO: Replace with employer-specific data fetched from a benefits provider
        //       or HR system. Plan data currently hardcoded to representative defaults.
        List<BenefitPlan> healthPlans = List.of(
            new BenefitPlan("MEDICAL", "PPO Platinum Plus", "Anthem",   "Family",      182.00, "PRE_TAX"),
            new BenefitPlan("DENTAL",  "Delta Premier",     "Delta Dental", "Family",   42.50, "PRE_TAX"),
            new BenefitPlan("VISION",  "VSP Vision Care",   "VSP",       "Employee + 1", 24.00, "PRE_TAX")
        );
        return ResponseEntity.ok(new BenefitPlansResponse(2024, "ACTIVE", healthPlans));
    }

    // ── GET /v1/benefits/employer-match ────────────────────────────────────────

    @GetMapping("/employer-match")
    @Operation(summary = "Return employer 401(k) match details")
    public ResponseEntity<EmployerMatchResponse> getEmployerMatch() {
        // TODO: Source from employer payroll configuration.
        return ResponseEntity.ok(new EmployerMatchResponse(
            4200.00, 100.0, 6.0,
            "Matched at 100% of your first 6% contribution."
        ));
    }

    // ── GET /v1/benefits/impact-analysis/{calculationId} ──────────────────────

    @GetMapping("/impact-analysis/{calculationId}")
    @Operation(summary = "Return the gross pay impact breakdown for a completed calculation")
    public ResponseEntity<ImpactAnalysisResponse> getImpactAnalysis(
            @PathVariable String calculationId) {

        Optional<CalculateResponse> stored = calculationStore.find(calculationId);
        if (stored.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        CalculateResponse calc = stored.get();
        double grossAnnual = calc.getGrossPerCadence();   // annual if cadence=ANNUAL; scale otherwise
        double netAnnual   = calc.getNetPerCadence();

        // Derive totals from line items
        double totalTaxes      = sumLineItems(calc.getLineItems(), "Income Tax", "Medicare",
                                              "Social Security", "FICA");
        double totalBenefits   = sumLineItems(calc.getLineItems(), "Medical", "Dental",
                                              "Vision", "401(k)", "HSA");
        double takeHome        = netAnnual;

        double total = takeHome + totalTaxes + totalBenefits;
        List<ImpactAnalysisEntry> breakdown = new ArrayList<>();
        if (total > 0) {
            breakdown.add(new ImpactAnalysisEntry("Take Home",  round(takeHome / total * 100)));
            breakdown.add(new ImpactAnalysisEntry("Taxes",      round(totalTaxes / total * 100)));
            breakdown.add(new ImpactAnalysisEntry("Benefits",   round(totalBenefits / total * 100)));
        }

        return ResponseEntity.ok(new ImpactAnalysisResponse(calculationId, breakdown));
    }

    // ── GET /v1/benefits/retirement/contributions ──────────────────────────────

    @GetMapping("/retirement/contributions")
    @Operation(summary = "Return current employee retirement deferral rates")
    public ResponseEntity<RetirementContributionsResponse> getRetirementContributions() {
        // TODO: Source from payroll system.
        RetirementTierResponse traditional = new RetirementTierResponse(8.0, 25.0);
        RetirementTierResponse roth        = new RetirementTierResponse(4.0, 25.0);
        return ResponseEntity.ok(new RetirementContributionsResponse(traditional, roth, 12.0));
    }

    // ── PUT /v1/benefits/retirement/contribution ───────────────────────────────

    @PutMapping("/retirement/contribution")
    @Operation(summary = "Update an employee retirement contribution rate")
    public ResponseEntity<Void> updateRetirementContribution(
            @Valid @RequestBody RetirementContributionRequest request) {
        // TODO: Persist to payroll system and trigger next-payroll recalculation.
        //       Validate type is TRADITIONAL_401K or ROTH_401K.
        if (!"TRADITIONAL_401K".equals(request.getType()) && !"ROTH_401K".equals(request.getType())) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.noContent().build();
    }

    // ── GET /v1/benefits/enrollment/window ────────────────────────────────────

    @GetMapping("/enrollment/window")
    @Operation(summary = "Return the current open enrollment window status")
    public ResponseEntity<EnrollmentWindowResponse> getEnrollmentWindow() {
        // TODO: Source from HR system. Currently returns a hardcoded open window.
        return ResponseEntity.ok(new EnrollmentWindowResponse(
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
