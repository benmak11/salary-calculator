package app.salary.calculator.engine;

import app.salary.calculator.client.RulePackClient;
import app.salary.calculator.registry.CalculatorRegistry;
import app.salary.common.constants.Country;
import app.salary.common.constants.PayCadence;
import app.salary.common.dto.*;
import app.salary.rules.RulePack;
import app.salary.rules.RulesRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CalculationOrchestratorTest {

    @Mock
    private RulesRegistry rulesRegistry;

    @Mock
    private CalculatorRegistry calculatorRegistry;

    @Mock
    private CountryCalculator countryCalculator;

    @Mock
    private RulePackClient rulePackClient;

    private CalculationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        // rulePackClient is null — orchestrator falls back to rulesRegistry (classpath)
        orchestrator = new CalculationOrchestrator(rulesRegistry, calculatorRegistry, null);
    }

    @Test
    void calculate_shouldReturnResponseWithCalculationId() {
        CalculateRequest request = createTestRequest();
        RulePack rulePack = new RulePack();
        CalculationResult result = createTestResult();

        when(rulesRegistry.getRulePack("UK", 2025)).thenReturn(rulePack);
        when(calculatorRegistry.getCalculator(Country.UK, 2025)).thenReturn(countryCalculator);
        when(countryCalculator.calculate(any(), eq(rulePack))).thenReturn(result);

        CalculateResponse response = orchestrator.calculate(request);

        assertNotNull(response);
        assertNotNull(response.getCalculationId());
        assertTrue(response.getCalculationId().startsWith("c_"));
    }

    @Test
    void calculate_withAnnualCadence_shouldNotDivideAmounts() {
        CalculateRequest request = createTestRequest();
        request.setCadence(PayCadence.ANNUAL);
        RulePack rulePack = new RulePack();
        CalculationResult result = createTestResult();

        when(rulesRegistry.getRulePack("UK", 2025)).thenReturn(rulePack);
        when(calculatorRegistry.getCalculator(Country.UK, 2025)).thenReturn(countryCalculator);
        when(countryCalculator.calculate(any(), eq(rulePack))).thenReturn(result);

        CalculateResponse response = orchestrator.calculate(request);

        assertEquals(50000.0, response.getGrossPerCadence(), 0.01);
        assertEquals(40000.0, response.getNetPerCadence(), 0.01);
    }

    @Test
    void calculate_withMonthlyCadence_shouldDivideBy12() {
        CalculateRequest request = createTestRequest();
        request.setCadence(PayCadence.MONTHLY);
        RulePack rulePack = new RulePack();
        CalculationResult result = createTestResult();

        when(rulesRegistry.getRulePack("UK", 2025)).thenReturn(rulePack);
        when(calculatorRegistry.getCalculator(Country.UK, 2025)).thenReturn(countryCalculator);
        when(countryCalculator.calculate(any(), eq(rulePack))).thenReturn(result);

        CalculateResponse response = orchestrator.calculate(request);

        assertEquals(50000.0 / 12, response.getGrossPerCadence(), 0.01);
        assertEquals(40000.0 / 12, response.getNetPerCadence(), 0.01);
    }

    @Test
    void calculate_withWeeklyCadence_shouldDivideBy52() {
        CalculateRequest request = createTestRequest();
        request.setCadence(PayCadence.WEEKLY);
        RulePack rulePack = new RulePack();
        CalculationResult result = createTestResult();

        when(rulesRegistry.getRulePack("UK", 2025)).thenReturn(rulePack);
        when(calculatorRegistry.getCalculator(Country.UK, 2025)).thenReturn(countryCalculator);
        when(countryCalculator.calculate(any(), eq(rulePack))).thenReturn(result);

        CalculateResponse response = orchestrator.calculate(request);

        assertEquals(50000.0 / 52, response.getGrossPerCadence(), 0.01);
        assertEquals(40000.0 / 52, response.getNetPerCadence(), 0.01);
    }

    @Test
    void calculate_withBiweeklyCadence_shouldDivideBy26() {
        CalculateRequest request = createTestRequest();
        request.setCadence(PayCadence.BIWEEKLY);
        RulePack rulePack = new RulePack();
        CalculationResult result = createTestResult();

        when(rulesRegistry.getRulePack("UK", 2025)).thenReturn(rulePack);
        when(calculatorRegistry.getCalculator(Country.UK, 2025)).thenReturn(countryCalculator);
        when(countryCalculator.calculate(any(), eq(rulePack))).thenReturn(result);

        CalculateResponse response = orchestrator.calculate(request);

        assertEquals(50000.0 / 26, response.getGrossPerCadence(), 0.01);
        assertEquals(40000.0 / 26, response.getNetPerCadence(), 0.01);
    }

    @Test
    void calculate_shouldSetCurrencyFromResult() {
        CalculateRequest request = createTestRequest();
        RulePack rulePack = new RulePack();
        CalculationResult result = createTestResult();

        when(rulesRegistry.getRulePack("UK", 2025)).thenReturn(rulePack);
        when(calculatorRegistry.getCalculator(Country.UK, 2025)).thenReturn(countryCalculator);
        when(countryCalculator.calculate(any(), eq(rulePack))).thenReturn(result);

        CalculateResponse response = orchestrator.calculate(request);

        assertEquals("GBP", response.getCurrency());
    }

    @Test
    void calculate_shouldSetRulePackVersionFromResult() {
        CalculateRequest request = createTestRequest();
        RulePack rulePack = new RulePack();
        CalculationResult result = createTestResult();

        when(rulesRegistry.getRulePack("UK", 2025)).thenReturn(rulePack);
        when(calculatorRegistry.getCalculator(Country.UK, 2025)).thenReturn(countryCalculator);
        when(countryCalculator.calculate(any(), eq(rulePack))).thenReturn(result);

        CalculateResponse response = orchestrator.calculate(request);

        assertEquals("UK-2025.1.0", response.getRulePackVersion());
    }

    @Test
    void calculate_shouldIncludeLineItemsAdjustedForCadence() {
        CalculateRequest request = createTestRequest();
        request.setCadence(PayCadence.MONTHLY);
        RulePack rulePack = new RulePack();
        CalculationResult result = createTestResult();
        result.addLineItem("Income Tax", 12000.0);

        when(rulesRegistry.getRulePack("UK", 2025)).thenReturn(rulePack);
        when(calculatorRegistry.getCalculator(Country.UK, 2025)).thenReturn(countryCalculator);
        when(countryCalculator.calculate(any(), eq(rulePack))).thenReturn(result);

        CalculateResponse response = orchestrator.calculate(request);

        assertFalse(response.getLineItems().isEmpty());
        assertEquals(1000.0, response.getLineItems().get(0).getAmount(), 0.01); // 12000 / 12
    }

    @Test
    void calculate_shouldIncludeExplanationsFromResult() {
        CalculateRequest request = createTestRequest();
        RulePack rulePack = new RulePack();
        CalculationResult result = createTestResult();
        result.addExplanation("tax_code", "Using tax code 1257L");

        when(rulesRegistry.getRulePack("UK", 2025)).thenReturn(rulePack);
        when(calculatorRegistry.getCalculator(Country.UK, 2025)).thenReturn(countryCalculator);
        when(countryCalculator.calculate(any(), eq(rulePack))).thenReturn(result);

        CalculateResponse response = orchestrator.calculate(request);

        assertFalse(response.getExplanation().isEmpty());
        assertEquals("tax_code", response.getExplanation().get(0).getId());
    }

    @Test
    void calculate_whenRulePackClientReturnsRulePack_shouldUseRemoteRulePack() {
        CalculationOrchestrator remoteOrchestrator =
                new CalculationOrchestrator(rulesRegistry, calculatorRegistry, rulePackClient);
        CalculateRequest request = createTestRequest();
        RulePack remotePack = new RulePack();
        CalculationResult result = createTestResult();

        when(rulePackClient.fetchLatest("UK", 2025)).thenReturn(Optional.of(remotePack));
        when(calculatorRegistry.getCalculator(Country.UK, 2025)).thenReturn(countryCalculator);
        when(countryCalculator.calculate(any(), eq(remotePack))).thenReturn(result);

        CalculateResponse response = remoteOrchestrator.calculate(request);

        assertNotNull(response);
        verify(rulePackClient).fetchLatest("UK", 2025);
        verify(rulesRegistry, never()).getRulePack(any(), anyInt());
    }

    @Test
    void calculate_whenRulePackClientReturnsEmpty_shouldFallBackToClasspath() {
        CalculationOrchestrator remoteOrchestrator =
                new CalculationOrchestrator(rulesRegistry, calculatorRegistry, rulePackClient);
        CalculateRequest request = createTestRequest();
        RulePack classPathPack = new RulePack();
        CalculationResult result = createTestResult();

        when(rulePackClient.fetchLatest("UK", 2025)).thenReturn(Optional.empty());
        when(rulesRegistry.getRulePack("UK", 2025)).thenReturn(classPathPack);
        when(calculatorRegistry.getCalculator(Country.UK, 2025)).thenReturn(countryCalculator);
        when(countryCalculator.calculate(any(), eq(classPathPack))).thenReturn(result);

        CalculateResponse response = remoteOrchestrator.calculate(request);

        assertNotNull(response);
        verify(rulePackClient).fetchLatest("UK", 2025);
        verify(rulesRegistry).getRulePack("UK", 2025);
    }

    private CalculateRequest createTestRequest() {
        CalculateRequest request = new CalculateRequest();
        request.setCountry(Country.UK);
        request.setTaxYear(2025);
        request.setAnnualSalary(50000.0);
        request.setCadence(PayCadence.ANNUAL);
        return request;
    }

    private CalculationResult createTestResult() {
        CalculationResult result = new CalculationResult();
        result.setGrossAnnual(50000.0);
        result.setNetAnnual(40000.0);
        result.setCurrency("GBP");
        result.setRulePackVersion("UK-2025.1.0");
        return result;
    }
}
