package app.salary.api.controller;

import app.salary.api.dto.InsightResponse;
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
class InsightsControllerTest {

    @Mock
    private CalculationStore calculationStore;

    private InsightsController controller;

    @BeforeEach
    void setUp() {
        controller = new InsightsController(calculationStore);
    }

    @Test
    void getInsight_withUnknownId_shouldReturn404() {
        when(calculationStore.find("c_unknown")).thenReturn(Optional.empty());

        ResponseEntity<InsightResponse> response = controller.getInsight("c_unknown");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void getInsight_withKnownId_shouldReturnInsight() {
        CalculateResponse calc = buildCalcResponse("c_abc123", 100000.0, 72000.0);
        when(calculationStore.find("c_abc123")).thenReturn(Optional.of(calc));

        ResponseEntity<InsightResponse> response = controller.getInsight("c_abc123");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("RETIREMENT_OPTIMIZATION", response.getBody().getType());
        assertEquals("Smart Saving Insight", response.getBody().getHeadline());
        assertEquals("Optimize 401k", response.getBody().getCtaLabel());
    }

    @Test
    void getInsight_monthlyTaxSavingsShouldBePositive() {
        CalculateResponse calc = buildCalcResponse("c_abc123", 120000.0, 80000.0);
        when(calculationStore.find("c_abc123")).thenReturn(Optional.of(calc));

        ResponseEntity<InsightResponse> response = controller.getInsight("c_abc123");

        assertNotNull(response.getBody());
        assertTrue(response.getBody().getMonthlyTaxSavings() > 0);
    }

    @Test
    void getInsight_annualRetirementBenefitShouldIncludeEmployerMatch() {
        // annualGross = 100000, 1% increase = 1000, with 100% employer match = 2000
        CalculateResponse calc = buildCalcResponse("c_abc123", 100000.0, 70000.0);
        when(calculationStore.find("c_abc123")).thenReturn(Optional.of(calc));

        ResponseEntity<InsightResponse> response = controller.getInsight("c_abc123");

        assertNotNull(response.getBody());
        assertEquals(2000.00, response.getBody().getAnnualRetirementBenefit());
    }

    @Test
    void getInsight_recommendedContributionShouldBeSeven() {
        CalculateResponse calc = buildCalcResponse("c_x", 80000.0, 60000.0);
        when(calculationStore.find("c_x")).thenReturn(Optional.of(calc));

        ResponseEntity<InsightResponse> response = controller.getInsight("c_x");

        assertNotNull(response.getBody());
        assertEquals(7.0, response.getBody().getRecommendedContributionPercent());
    }

    private CalculateResponse buildCalcResponse(String id, double gross, double net) {
        CalculateResponse calc = new CalculateResponse();
        calc.setCalculationId(id);
        calc.setGrossPerCadence(gross);
        calc.setNetPerCadence(net);
        calc.setLineItems(List.of(
            new LineItem("Federal Income Tax", gross * 0.20),
            new LineItem("Medicare",           gross * 0.0145)
        ));
        return calc;
    }
}
