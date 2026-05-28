package app.salary.api.controller;

import app.salary.api.dto.*;
import app.salary.api.service.CalculationStore;
import app.salary.common.dto.CalculateResponse;
import app.salary.common.dto.LineItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BenefitsControllerTest {

    @Mock
    private CalculationStore calculationStore;

    private BenefitsController controller;

    @BeforeEach
    void setUp() {
        controller = new BenefitsController(calculationStore);
    }

    // ── /plans ────────────────────────────────────────────────────────────────────

    @Test
    void getBenefitPlans_shouldReturnThreeHealthPlans() {
        ResponseEntity<BenefitPlansResponse> response = controller.getBenefitPlans();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(3, response.getBody().getHealth().size());
        assertEquals("ACTIVE", response.getBody().getEnrollmentStatus());
        assertEquals(2024, response.getBody().getPlanYear());
    }

    @Test
    void getBenefitPlans_shouldIncludeMedicalDentalVision() {
        ResponseEntity<BenefitPlansResponse> response = controller.getBenefitPlans();

        List<BenefitPlan> plans = response.getBody().getHealth();
        assertTrue(plans.stream().anyMatch(p -> "MEDICAL".equals(p.getType())));
        assertTrue(plans.stream().anyMatch(p -> "DENTAL".equals(p.getType())));
        assertTrue(plans.stream().anyMatch(p -> "VISION".equals(p.getType())));
    }

    @Test
    void getBenefitPlans_shouldMarkAllPlansAsPreTax() {
        ResponseEntity<BenefitPlansResponse> response = controller.getBenefitPlans();

        response.getBody().getHealth().forEach(plan ->
            assertEquals("PRE_TAX", plan.getTaxTreatment()));
    }

    // ── /employer-match ───────────────────────────────────────────────────────────

    @Test
    void getEmployerMatch_shouldReturnCorrectDefaults() {
        ResponseEntity<EmployerMatchResponse> response = controller.getEmployerMatch();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(4200.00, response.getBody().getAnnualMatchAmount());
        assertEquals(100.0,   response.getBody().getMatchPercent());
        assertEquals(6.0,     response.getBody().getMatchThresholdPercent());
        assertNotNull(response.getBody().getMatchDescription());
    }

    // ── /impact-analysis/{calculationId} ─────────────────────────────────────────

    @Test
    void getImpactAnalysis_withUnknownId_shouldReturn404() {
        when(calculationStore.find("c_unknown")).thenReturn(Optional.empty());

        ResponseEntity<ImpactAnalysisResponse> response =
            controller.getImpactAnalysis("c_unknown");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void getImpactAnalysis_withKnownId_shouldReturnBreakdown() {
        CalculateResponse calc = new CalculateResponse();
        calc.setCalculationId("c_abc123");
        calc.setGrossPerCadence(100000.0);
        calc.setNetPerCadence(72000.0);
        calc.setLineItems(List.of(
            new LineItem("Federal Income Tax", 15000.0),
            new LineItem("Medicare", 1450.0),
            new LineItem("Medical Premium", 2184.0)
        ));
        when(calculationStore.find("c_abc123")).thenReturn(Optional.of(calc));

        ResponseEntity<ImpactAnalysisResponse> response =
            controller.getImpactAnalysis("c_abc123");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("c_abc123", response.getBody().getCalculationId());
        assertFalse(response.getBody().getBreakdown().isEmpty());
    }

    // ── /retirement/contributions ─────────────────────────────────────────────────

    @Test
    void getRetirementContributions_shouldReturnDefaultRates() {
        ResponseEntity<RetirementContributionsResponse> response =
            controller.getRetirementContributions();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(8.0, response.getBody().getTraditional401k().getEmployeePercent());
        assertEquals(4.0, response.getBody().getRoth401k().getEmployeePercent());
        assertEquals(12.0, response.getBody().getTotalContributionPercent());
    }

    // ── /retirement/contribution PUT ─────────────────────────────────────────────

    @Test
    void updateRetirementContribution_withValidType_shouldReturn204() {
        RetirementContributionRequest request = new RetirementContributionRequest();
        request.setType("TRADITIONAL_401K");
        request.setEmployeePercent(9.0);

        ResponseEntity<Void> response = controller.updateRetirementContribution(request);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    void updateRetirementContribution_withRothType_shouldReturn204() {
        RetirementContributionRequest request = new RetirementContributionRequest();
        request.setType("ROTH_401K");
        request.setEmployeePercent(5.0);

        ResponseEntity<Void> response = controller.updateRetirementContribution(request);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    void updateRetirementContribution_withInvalidType_shouldReturn400() {
        RetirementContributionRequest request = new RetirementContributionRequest();
        request.setType("UNKNOWN_TYPE");
        request.setEmployeePercent(5.0);

        ResponseEntity<Void> response = controller.updateRetirementContribution(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // ── /enrollment/window ───────────────────────────────────────────────────────

    @Test
    void getEnrollmentWindow_shouldReturnOpenStatus() {
        ResponseEntity<EnrollmentWindowResponse> response = controller.getEnrollmentWindow();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("OPEN", response.getBody().getStatus());
        assertEquals(14, response.getBody().getDaysRemaining());
        assertNotNull(response.getBody().getEffectiveChangeDate());
    }
}
