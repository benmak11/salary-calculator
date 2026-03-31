package app.salary.calculator.countries;

import app.salary.calculator.engine.CalculationInput;
import app.salary.calculator.engine.CalculationResult;
import app.salary.calculator.shared.DeductionCalculator;
import app.salary.calculator.shared.TaxBracketCalculator;
import app.salary.common.constants.Country;
import app.salary.common.constants.FilingStatus;
import app.salary.common.dto.CountryOptionsUS;
import app.salary.common.dto.NamedDeduction;
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
        rulePack = new RulePack();

        RulePack.Metadata metadata = new RulePack.Metadata();
        metadata.setVersion("US-2025.10.0");
        rulePack.setMetadata(metadata);

        RulePack.Federal federal = new RulePack.Federal();
        Map<String, Double> standardDeductions = new HashMap<>();
        standardDeductions.put("SINGLE", 14600.0);
        standardDeductions.put("MARRIED", 29200.0);
        standardDeductions.put("HEAD_OF_HOUSEHOLD", 21900.0);
        federal.setStandardDeductions(standardDeductions);
        federal.setSupplementalWithholdingRate(0.22);

        List<RulePack.TaxBracket> brackets = new ArrayList<>();
        RulePack.TaxBracket bracket1 = new RulePack.TaxBracket();
        bracket1.setUpTo(11600.0);
        bracket1.setRate(0.10);
        brackets.add(bracket1);
        federal.setBrackets(brackets);

        // HoH-specific brackets
        List<RulePack.TaxBracket> hohBrackets = new ArrayList<>();
        RulePack.TaxBracket hohBracket = new RulePack.TaxBracket();
        hohBracket.setUpTo(16550.0);
        hohBracket.setRate(0.10);
        hohBrackets.add(hohBracket);
        Map<String, List<RulePack.TaxBracket>> byStatus = new HashMap<>();
        byStatus.put("HEAD_OF_HOUSEHOLD", hohBrackets);
        federal.setBracketsByFilingStatus(byStatus);

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

        input = new CalculationInput();
        input.setAnnualGross(100000.0);
        input.setBonusAnnual(0.0);
        input.setPretax(new Pretax());
        input.setPosttax(new Posttax());

        CountryOptionsUS usOptions = new CountryOptionsUS();
        usOptions.setState("CA");
        usOptions.setFilingStatus(FilingStatus.SINGLE);
        input.setUsOptions(usOptions);
    }

    // ── supports() ───────────────────────────────────────────────────────────────

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

    // ── basic calculation ─────────────────────────────────────────────────────────

    @Test
    void calculate_shouldUseAnnualGrossDirectly() {
        when(deductionCalculator.calculatePretaxDeductions(any(), eq(100000.0))).thenReturn(5000.0);
        when(deductionCalculator.calculateGenericPretaxDeductions(any(), eq(100000.0))).thenReturn(0.0);
        when(deductionCalculator.calculatePensionContribution(any(), eq(100000.0))).thenReturn(0.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(100.0);
        when(bracketCalculator.calculateTax(anyDouble(), anyList())).thenReturn(10000.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        assertNotNull(result);
        assertEquals(100000.0, result.getGrossAnnual());
        assertEquals("USD", result.getCurrency());
        assertEquals("US-2025.10.0", result.getRulePackVersion());
        verify(deductionCalculator).calculatePretaxDeductions(any(), eq(100000.0));
    }

    @Test
    void calculate_setsBaseSalaryAndBonusOnResult() {
        input.setBonusAnnual(10000.0);
        input.setAnnualGross(110000.0);

        when(deductionCalculator.calculatePretaxDeductions(any(), eq(110000.0))).thenReturn(0.0);
        when(deductionCalculator.calculateGenericPretaxDeductions(any(), eq(110000.0))).thenReturn(0.0);
        when(deductionCalculator.calculatePensionContribution(any(), eq(110000.0))).thenReturn(0.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);
        when(bracketCalculator.calculateTax(anyDouble(), anyList())).thenReturn(10000.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        assertEquals(110000.0, result.getGrossAnnual());
        assertEquals(100000.0, result.getBaseSalaryAnnual());
        assertEquals(10000.0, result.getBonusAnnual());
    }

    @Test
    void calculate_withNoPretaxDeductions_shouldCalculateCorrectly() {
        when(deductionCalculator.calculatePretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculateGenericPretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculatePensionContribution(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);
        when(bracketCalculator.calculateTax(anyDouble(), anyList())).thenReturn(15000.0);

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
        when(deductionCalculator.calculateGenericPretaxDeductions(any(), eq(100000.0))).thenReturn(0.0);
        when(deductionCalculator.calculatePensionContribution(any(), eq(100000.0))).thenReturn(0.0);
        when(bracketCalculator.calculateTax(eq(95000.0 - 14600.0), anyList())).thenReturn(12000.0);
        when(bracketCalculator.calculateTax(eq(95000.0), anyList())).thenReturn(8000.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        calculator.calculate(input, rulePack);

        verify(deductionCalculator).calculatePretaxDeductions(any(), eq(100000.0));
        // Federal tax calculated on taxable base (no bonus) after standard deduction
        verify(bracketCalculator).calculateTax(eq(95000.0 - 14600.0), anyList());
    }

    // ── filing status ─────────────────────────────────────────────────────────────

    @Test
    void calculate_withMarriedFilingStatus_shouldUseMarriedDeduction() {
        CountryOptionsUS usOptions = new CountryOptionsUS();
        usOptions.setState("CA");
        usOptions.setFilingStatus(FilingStatus.MARRIED);
        input.setUsOptions(usOptions);

        when(deductionCalculator.calculatePretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculateGenericPretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculatePensionContribution(any(), anyDouble())).thenReturn(0.0);
        when(bracketCalculator.calculateTax(anyDouble(), anyList())).thenReturn(10000.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        calculator.calculate(input, rulePack);

        verify(bracketCalculator).calculateTax(eq(100000.0 - 29200.0), anyList());
    }

    @Test
    void calculate_withHeadOfHouseholdFilingStatus_shouldUseHoHDeductionAndBrackets() {
        CountryOptionsUS usOptions = new CountryOptionsUS();
        usOptions.setState("CA");
        usOptions.setFilingStatus(FilingStatus.HEAD_OF_HOUSEHOLD);
        input.setUsOptions(usOptions);

        when(deductionCalculator.calculatePretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculateGenericPretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculatePensionContribution(any(), anyDouble())).thenReturn(0.0);
        when(bracketCalculator.calculateTax(anyDouble(), anyList())).thenReturn(10000.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        assertNotNull(result);
        // Federal brackets called with HoH standard deduction applied
        verify(bracketCalculator).calculateTax(eq(100000.0 - 21900.0), anyList());
        // Explanation references HEAD_OF_HOUSEHOLD
        boolean hohExplanation = result.getExplanations().stream()
            .anyMatch(e -> e.getId().equals("fed_tax_brackets")
                       && e.getText().contains("HEAD_OF_HOUSEHOLD"));
        assertTrue(hohExplanation);
    }

    // ── bonus / supplemental withholding ─────────────────────────────────────────

    @Test
    void calculate_withBonus_shouldApplySupplementalWithholdingAndEmitExplanation() {
        input.setBonusAnnual(20000.0);
        input.setAnnualGross(120000.0); // 100k base + 20k bonus

        when(deductionCalculator.calculatePretaxDeductions(any(), eq(120000.0))).thenReturn(0.0);
        when(deductionCalculator.calculateGenericPretaxDeductions(any(), eq(120000.0))).thenReturn(0.0);
        when(deductionCalculator.calculatePensionContribution(any(), eq(120000.0))).thenReturn(0.0);
        when(bracketCalculator.calculateTax(anyDouble(), anyList())).thenReturn(10000.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        // Federal Income Tax line item should include both bracket tax + 22% bonus tax
        double expectedBonusTax = 20000.0 * 0.22;
        boolean federalItem = result.getLineItems().stream()
            .anyMatch(item -> "Federal Income Tax".equals(item.getName())
                           && item.getAmount() == 10000.0 + expectedBonusTax);
        assertTrue(federalItem, "Expected Federal Income Tax to include bonus withholding");

        boolean hasExplanation = result.getExplanations().stream()
            .anyMatch(e -> "bonus_withholding".equals(e.getId()));
        assertTrue(hasExplanation, "Expected bonus_withholding explanation");
    }

    @Test
    void calculate_withZeroBonus_shouldNotEmitBonusWithholdingExplanation() {
        when(deductionCalculator.calculatePretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculateGenericPretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculatePensionContribution(any(), anyDouble())).thenReturn(0.0);
        when(bracketCalculator.calculateTax(anyDouble(), anyList())).thenReturn(10000.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        boolean noBonusExplanation = result.getExplanations().stream()
            .noneMatch(e -> "bonus_withholding".equals(e.getId()));
        assertTrue(noBonusExplanation);
    }

    // ── individual benefit line items ─────────────────────────────────────────────

    @Test
    void calculate_withMedicalDentalVision_shouldEmitIndividualLineItems() {
        Pretax pretax = new Pretax();
        pretax.setMedical(2184.0);
        pretax.setDental(510.0);
        pretax.setVision(288.0);
        input.setPretax(pretax);

        when(deductionCalculator.calculatePretaxDeductions(any(), eq(100000.0))).thenReturn(2982.0);
        when(deductionCalculator.calculateGenericPretaxDeductions(any(), eq(100000.0))).thenReturn(0.0);
        when(deductionCalculator.calculatePensionContribution(any(), eq(100000.0))).thenReturn(0.0);
        when(bracketCalculator.calculateTax(anyDouble(), anyList())).thenReturn(10000.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        assertTrue(result.getLineItems().stream()
            .anyMatch(i -> "Medical Premium".equals(i.getName()) && i.getAmount() == 2184.0));
        assertTrue(result.getLineItems().stream()
            .anyMatch(i -> "Dental Premium".equals(i.getName()) && i.getAmount() == 510.0));
        assertTrue(result.getLineItems().stream()
            .anyMatch(i -> "Vision Premium".equals(i.getName()) && i.getAmount() == 288.0));
    }

    @Test
    void calculate_withPension_shouldEmitEmployee401kLineItem() {
        Pretax pretax = new Pretax();
        pretax.setPensionPercent(0.06);
        input.setPretax(pretax);

        when(deductionCalculator.calculatePretaxDeductions(any(), eq(100000.0))).thenReturn(6000.0);
        when(deductionCalculator.calculateGenericPretaxDeductions(any(), eq(100000.0))).thenReturn(0.0);
        when(deductionCalculator.calculatePensionContribution(any(), eq(100000.0))).thenReturn(6000.0);
        when(bracketCalculator.calculateTax(anyDouble(), anyList())).thenReturn(10000.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        assertTrue(result.getLineItems().stream()
            .anyMatch(i -> "Employee 401(k)".equals(i.getName()) && i.getAmount() == 6000.0));
    }

    @Test
    void calculate_withNamedCustomDeductions_shouldEmitEachAsOwnLineItem() {
        Pretax pretax = new Pretax();
        List<NamedDeduction> customs = new ArrayList<>();
        customs.add(new NamedDeduction("Commuter Benefit", 1200.0));
        customs.add(new NamedDeduction("Parking", 600.0));
        pretax.setCustomDeductions(customs);
        input.setPretax(pretax);

        when(deductionCalculator.calculatePretaxDeductions(any(), eq(100000.0))).thenReturn(1800.0);
        when(deductionCalculator.calculateGenericPretaxDeductions(any(), eq(100000.0))).thenReturn(0.0);
        when(deductionCalculator.calculatePensionContribution(any(), eq(100000.0))).thenReturn(0.0);
        when(bracketCalculator.calculateTax(anyDouble(), anyList())).thenReturn(10000.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        assertTrue(result.getLineItems().stream()
            .anyMatch(i -> "Commuter Benefit".equals(i.getName()) && i.getAmount() == 1200.0));
        assertTrue(result.getLineItems().stream()
            .anyMatch(i -> "Parking".equals(i.getName()) && i.getAmount() == 600.0));
    }

    // ── FICA edge cases ───────────────────────────────────────────────────────────

    @Test
    void calculate_withHighIncome_shouldCalculateAdditionalMedicare() {
        input.setAnnualGross(250000.0);
        input.setBonusAnnual(0.0);

        when(deductionCalculator.calculatePretaxDeductions(any(), eq(250000.0))).thenReturn(0.0);
        when(deductionCalculator.calculateGenericPretaxDeductions(any(), eq(250000.0))).thenReturn(0.0);
        when(deductionCalculator.calculatePensionContribution(any(), eq(250000.0))).thenReturn(0.0);
        when(bracketCalculator.calculateTax(anyDouble(), anyList())).thenReturn(30000.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        assertTrue(result.getExplanations().stream()
            .anyMatch(e -> "additional_medicare".equals(e.getId())));
    }

    @Test
    void calculate_withSocialSecurityAboveWageBase_shouldCapSS() {
        input.setAnnualGross(200000.0);
        input.setBonusAnnual(0.0);

        when(deductionCalculator.calculatePretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculateGenericPretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculatePensionContribution(any(), anyDouble())).thenReturn(0.0);
        when(bracketCalculator.calculateTax(anyDouble(), anyList())).thenReturn(20000.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        assertTrue(result.getLineItems().stream()
            .anyMatch(item -> "FICA (Social Security)".equals(item.getName())
                           && item.getAmount() == 168600.0 * 0.062));
    }

    @Test
    void calculate_withPosttaxDeductions_shouldSubtractFromNet() {
        when(deductionCalculator.calculatePretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculateGenericPretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculatePensionContribution(any(), anyDouble())).thenReturn(0.0);
        when(bracketCalculator.calculateTax(anyDouble(), anyList())).thenReturn(10000.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(500.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        assertTrue(result.getLineItems().stream()
            .anyMatch(item -> "Post-tax Deductions".equals(item.getName())));
    }

    @Test
    void getCountryCode_shouldReturnUS() {
        assertEquals("US", calculator.getCountryCode());
    }
}
