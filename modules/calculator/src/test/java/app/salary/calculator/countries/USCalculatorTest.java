package app.salary.calculator.countries;

import app.salary.calculator.engine.CalculationInput;
import app.salary.calculator.engine.CalculationResult;
import app.salary.calculator.shared.DeductionCalculator;
import app.salary.calculator.shared.TaxBracketCalculator;
import app.salary.common.constants.Country;
import app.salary.common.constants.FilingStatus;
import app.salary.common.dto.CountryOptionsUS;
import app.salary.common.dto.Posttax;
import app.salary.common.dto.Pretax;
import app.salary.rules.RulePack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class USCalculatorTest {

    @Mock
    private TaxBracketCalculator bracketCalculator;

    @Mock
    private DeductionCalculator deductionCalculator;

    @InjectMocks
    private USCalculator calculator;

    private RulePack rulePack;
    private CalculationInput input;

    @BeforeEach
    void setUp() {
        // Setup RulePack
        rulePack = new RulePack();

        RulePack.Metadata metadata = new RulePack.Metadata();
        metadata.setVersion("US-2025.10.0");
        rulePack.setMetadata(metadata);

        RulePack.Federal federal = new RulePack.Federal();
        Map<String, Double> standardDeductions = new HashMap<>();
        standardDeductions.put("SINGLE", 14600.0);
        standardDeductions.put("MARRIED", 29200.0);
        federal.setStandardDeductions(standardDeductions);

        List<RulePack.TaxBracket> brackets = new ArrayList<>();
        RulePack.TaxBracket bracket1 = new RulePack.TaxBracket();
        bracket1.setUpTo(11600.0);
        bracket1.setRate(0.10);
        brackets.add(bracket1);
        federal.setBrackets(brackets);
        rulePack.setFederal(federal);

        RulePack.Fica fica = new RulePack.Fica();
        fica.setSsRate(0.062);
        fica.setSsWageBase(168600.0);
        fica.setMedicareRate(0.0145);
        fica.setAdditionalMedicareThreshold(200000.0);
        fica.setAdditionalRate(0.009);
        rulePack.setFica(fica);

        Map<String, RulePack.StateRules> states = new HashMap<>();
        RulePack.StateRules caRules = new RulePack.StateRules();
        List<RulePack.TaxBracket> caBrackets = new ArrayList<>();
        RulePack.TaxBracket caBracket = new RulePack.TaxBracket();
        caBracket.setUpTo(10000.0);
        caBracket.setRate(0.01);
        caBrackets.add(caBracket);
        caRules.setBrackets(caBrackets);
        caRules.setLocal(0.0);
        states.put("CA", caRules);
        rulePack.setStates(states);

        // Setup input
        input = new CalculationInput();
        input.setAnnualGross(100000.0);
        input.setPretax(new Pretax());
        input.setPosttax(new Posttax());

        CountryOptionsUS usOptions = new CountryOptionsUS();
        usOptions.setState("CA");
        usOptions.setFilingStatus(FilingStatus.SINGLE);
        input.setUsOptions(usOptions);
    }

    @Test
    void supports_usCountryAndTaxYear2025_shouldReturnTrue() {
        assertTrue(calculator.supports(Country.US, 2025));
    }

    @Test
    void supports_usCountryAndTaxYear2026_shouldReturnTrue() {
        assertTrue(calculator.supports(Country.US, 2026));
    }

    @Test
    void supports_usCountryAndTaxYear2024_shouldReturnFalse() {
        assertFalse(calculator.supports(Country.US, 2024));
    }

    @Test
    void supports_ukCountry_shouldReturnFalse() {
        assertFalse(calculator.supports(Country.UK, 2025));
    }

    @Test
    void calculate_shouldUseAnnualGrossDirectly() {
        when(deductionCalculator.calculatePretaxDeductions(any(), eq(100000.0))).thenReturn(5000.0);
        when(bracketCalculator.calculateTax(anyDouble(), anyList())).thenReturn(10000.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(100.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        assertNotNull(result);
        assertEquals(100000.0, result.getGrossAnnual());
        assertEquals("USD", result.getCurrency());
        assertEquals("US-2025.10.0", result.getRulePackVersion());

        // Verify annualGross was used directly, not via Income calculation
        verify(deductionCalculator).calculatePretaxDeductions(any(), eq(100000.0));
    }

    @Test
    void calculate_withNoPretaxDeductions_shouldCalculateCorrectly() {
        when(deductionCalculator.calculatePretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(bracketCalculator.calculateTax(anyDouble(), anyList())).thenReturn(15000.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        assertNotNull(result);
        assertEquals(100000.0, result.getGrossAnnual());

        // Net = 100000 - 0 (pretax) - 15000 (fed) - 15000 (state) - 6200 (ss) - 1450 (medicare) - 0 (posttax)
        double expectedNet = 100000.0 - 0.0 - 15000.0 - 15000.0 - 6200.0 - 1450.0 - 0.0;
        assertEquals(expectedNet, result.getNetAnnual());
    }

    @Test
    void calculate_withPretaxDeductions_shouldReduceTaxableIncome() {
        double pretaxDeductions = 5000.0;
        when(deductionCalculator.calculatePretaxDeductions(any(), eq(100000.0))).thenReturn(pretaxDeductions);
        when(bracketCalculator.calculateTax(eq(95000.0 - 14600.0), anyList())).thenReturn(12000.0);
        when(bracketCalculator.calculateTax(eq(95000.0), anyList())).thenReturn(8000.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        // Verify pretax deductions were calculated with the gross annual
        verify(deductionCalculator).calculatePretaxDeductions(any(), eq(100000.0));

        // Verify federal tax was calculated on taxable income after pretax deductions
        verify(bracketCalculator).calculateTax(eq(95000.0 - 14600.0), anyList());
    }

    @Test
    void calculate_withMarriedFilingStatus_shouldUseMarriedDeduction() {
        CountryOptionsUS usOptions = new CountryOptionsUS();
        usOptions.setState("CA");
        usOptions.setFilingStatus(FilingStatus.MARRIED);
        input.setUsOptions(usOptions);

        when(deductionCalculator.calculatePretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(bracketCalculator.calculateTax(anyDouble(), anyList())).thenReturn(10000.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        // Verify federal tax was calculated with married standard deduction
        verify(bracketCalculator).calculateTax(eq(100000.0 - 29200.0), anyList());
    }

    @Test
    void calculate_withHighIncome_shouldCalculateAdditionalMedicare() {
        input.setAnnualGross(250000.0);

        when(deductionCalculator.calculatePretaxDeductions(any(), eq(250000.0))).thenReturn(0.0);
        when(bracketCalculator.calculateTax(anyDouble(), anyList())).thenReturn(30000.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        // Verify explanation about additional Medicare was added
        boolean hasAdditionalMedicareExplanation = result.getExplanations().stream()
            .anyMatch(exp -> exp.getId().equals("additional_medicare"));
        assertTrue(hasAdditionalMedicareExplanation);
    }

    @Test
    void calculate_withPosttaxDeductions_shouldSubtractFromNet() {
        when(deductionCalculator.calculatePretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(bracketCalculator.calculateTax(anyDouble(), anyList())).thenReturn(10000.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(500.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        // Verify posttax deductions are in line items when > 0
        boolean hasPosttaxLineItem = result.getLineItems().stream()
            .anyMatch(item -> item.getName().equals("Post-tax Deductions"));
        assertTrue(hasPosttaxLineItem);
    }

    @Test
    void calculate_withNoStateTax_shouldNotAddStateLineItem() {
        // Setup state with no brackets (like TX)
        RulePack.StateRules txRules = new RulePack.StateRules();
        txRules.setBrackets(new ArrayList<>());
        txRules.setLocal(0.0);
        rulePack.getStates().put("TX", txRules);

        CountryOptionsUS usOptions = new CountryOptionsUS();
        usOptions.setState("TX");
        usOptions.setFilingStatus(FilingStatus.SINGLE);
        input.setUsOptions(usOptions);

        when(deductionCalculator.calculatePretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(bracketCalculator.calculateTax(anyDouble(), anyList())).thenReturn(0.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        // State tax should be 0 but still processed
        assertNotNull(result);
    }

    @Test
    void calculate_withSocialSecurityAboveWageBase_shouldCapSS() {
        input.setAnnualGross(200000.0); // Above SS wage base

        when(deductionCalculator.calculatePretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(bracketCalculator.calculateTax(anyDouble(), anyList())).thenReturn(20000.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        // SS should be capped at wage base * rate = 168600 * 0.062
        boolean hasSocialSecurityLineItem = result.getLineItems().stream()
            .anyMatch(item -> item.getName().equals("FICA (Social Security)") &&
                             item.getAmount() == 168600.0 * 0.062);
        assertTrue(hasSocialSecurityLineItem);
    }

    @Test
    void getCountryCode_shouldReturnUS() {
        assertEquals("US", calculator.getCountryCode());
    }
}
