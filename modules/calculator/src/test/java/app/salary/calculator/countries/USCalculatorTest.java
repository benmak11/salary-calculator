package app.salary.calculator.countries;

import app.salary.calculator.engine.CalculationInput;
import app.salary.calculator.engine.CalculationResult;
import app.salary.calculator.shared.DeductionCalculator;
import app.salary.calculator.shared.TaxBracketCalculator;
import app.salary.common.constants.Country;
import app.salary.common.constants.FilingStatus;
import app.salary.common.constants.PayCadence;
import app.salary.common.dto.CountryOptionsUS;
import app.salary.common.dto.LineItem;
import app.salary.common.dto.LineItemCategory;
import app.salary.common.dto.NamedDeduction;
import app.salary.common.dto.Posttax;
import app.salary.common.dto.Pretax;
import app.salary.common.dto.W4;
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
            .anyMatch(e -> "supplemental_withholding".equals(e.getId()));
        assertTrue(hasExplanation, "Expected supplemental_withholding explanation");
    }

    @Test
    void calculate_withZeroBonus_shouldNotEmitSupplementalWithholdingExplanation() {
        when(deductionCalculator.calculatePretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculateGenericPretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculatePensionContribution(any(), anyDouble())).thenReturn(0.0);
        when(bracketCalculator.calculateTax(anyDouble(), anyList())).thenReturn(10000.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        boolean noSupplementalExplanation = result.getExplanations().stream()
            .noneMatch(e -> "supplemental_withholding".equals(e.getId()));
        assertTrue(noSupplementalExplanation);
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
            .anyMatch(i -> "401(k)".equals(i.getName()) && i.getAmount() == 6000.0));
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
            .anyMatch(item -> "Social Security".equals(item.getName())
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

    // ── Earnings line items (Phase 2) ─────────────────────────────────────────────

    @Test
    void calculate_withSalaryBreakdown_shouldEmitSalaryLineItem() {
        input.setSalaryAnnual(100000.0);
        when(deductionCalculator.calculatePretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculateGenericPretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculatePensionContribution(any(), anyDouble())).thenReturn(0.0);
        when(bracketCalculator.calculateTax(anyDouble(), anyList())).thenReturn(10000.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        LineItem salary = findLineItem(result, "Salary");
        assertNotNull(salary, "Expected Salary earnings line item");
        assertEquals(100000.0, salary.getAmount(), 0.01);
        assertEquals(LineItemCategory.EARNINGS, salary.getCategory());
    }

    @Test
    void calculate_withBonusAndCommission_shouldEmitSeparateEarningsLineItems() {
        input.setAnnualGross(115000.0);
        input.setSalaryAnnual(100000.0);
        input.setBonusAnnual(10000.0);
        input.setCommissionAnnual(5000.0);

        when(deductionCalculator.calculatePretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculateGenericPretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculatePensionContribution(any(), anyDouble())).thenReturn(0.0);
        when(bracketCalculator.calculateTax(anyDouble(), anyList())).thenReturn(10000.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        LineItem bonus = findLineItem(result, "Bonus");
        LineItem commission = findLineItem(result, "Commission");
        assertNotNull(bonus);
        assertNotNull(commission);
        assertEquals(10000.0, bonus.getAmount(), 0.01);
        assertEquals(5000.0, commission.getAmount(), 0.01);
        assertEquals(LineItemCategory.EARNINGS, bonus.getCategory());
        assertEquals(LineItemCategory.EARNINGS, commission.getCategory());
    }

    @Test
    void calculate_withHourlyOvertime_shouldEmitOvertimeAndDoubleTimeLineItems() {
        input.setAnnualGross(60000.0);
        input.setSalaryAnnual(40000.0);   // regular hours pay
        input.setOvertimeAnnual(15000.0);
        input.setDoubleTimeAnnual(5000.0);

        when(deductionCalculator.calculatePretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculateGenericPretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculatePensionContribution(any(), anyDouble())).thenReturn(0.0);
        when(bracketCalculator.calculateTax(anyDouble(), anyList())).thenReturn(5000.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        assertNotNull(findLineItem(result, "Overtime"));
        assertNotNull(findLineItem(result, "Double Time"));
        assertEquals(LineItemCategory.EARNINGS, findLineItem(result, "Overtime").getCategory());
    }

    // ── FSA + DCA + HSA (Phase 2) ─────────────────────────────────────────────────

    @Test
    void calculate_withHealthcareFsa_shouldEmitAsPretaxBenefit() {
        Pretax pretax = new Pretax();
        pretax.setHealthcareFsa(2600.0);
        input.setPretax(pretax);

        when(deductionCalculator.calculatePretaxDeductions(any(), anyDouble())).thenReturn(2600.0);
        when(deductionCalculator.calculateGenericPretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculatePensionContribution(any(), anyDouble())).thenReturn(0.0);
        when(bracketCalculator.calculateTax(anyDouble(), anyList())).thenReturn(10000.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        LineItem fsa = findLineItem(result, "Healthcare FSA");
        assertNotNull(fsa);
        assertEquals(2600.0, fsa.getAmount(), 0.01);
        assertEquals(LineItemCategory.PRE_TAX_BENEFIT, fsa.getCategory());
    }

    @Test
    void calculate_withDependentCareFsa_shouldEmitAsPretaxBenefit() {
        Pretax pretax = new Pretax();
        pretax.setDependentCareFsa(5000.0);
        input.setPretax(pretax);

        when(deductionCalculator.calculatePretaxDeductions(any(), anyDouble())).thenReturn(5000.0);
        when(deductionCalculator.calculateGenericPretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculatePensionContribution(any(), anyDouble())).thenReturn(0.0);
        when(bracketCalculator.calculateTax(anyDouble(), anyList())).thenReturn(10000.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        LineItem dca = findLineItem(result, "Dependent Care FSA");
        assertNotNull(dca);
        assertEquals(5000.0, dca.getAmount(), 0.01);
        assertEquals(LineItemCategory.PRE_TAX_BENEFIT, dca.getCategory());
    }

    @Test
    void calculate_withHsa_shouldEmitAsPretaxBenefit() {
        Pretax pretax = new Pretax();
        pretax.setHsa(3850.0);
        input.setPretax(pretax);

        when(deductionCalculator.calculatePretaxDeductions(any(), anyDouble())).thenReturn(3850.0);
        when(deductionCalculator.calculateGenericPretaxDeductions(any(), anyDouble())).thenReturn(3850.0);
        when(deductionCalculator.calculatePensionContribution(any(), anyDouble())).thenReturn(0.0);
        when(bracketCalculator.calculateTax(anyDouble(), anyList())).thenReturn(10000.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        LineItem hsa = findLineItem(result, "HSA Contribution");
        assertNotNull(hsa);
        assertEquals(LineItemCategory.PRE_TAX_BENEFIT, hsa.getCategory());
    }

    // ── Roth 401(k) (Phase 2) ─────────────────────────────────────────────────────

    @Test
    void calculate_withRoth401k_shouldEmitRetirementLineItemAndReduceNet() {
        input.setSalaryAnnual(100000.0);
        Posttax posttax = new Posttax();
        posttax.setRoth401kPercent(0.04); // 4%
        input.setPosttax(posttax);

        when(deductionCalculator.calculatePretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculateGenericPretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculatePensionContribution(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculateRoth401k(any(), anyDouble())).thenReturn(4000.0);
        when(bracketCalculator.calculateTax(anyDouble(), anyList())).thenReturn(10000.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        LineItem roth = findLineItem(result, "Roth 401(k)");
        assertNotNull(roth);
        assertEquals(4000.0, roth.getAmount(), 0.01);
        assertEquals(LineItemCategory.RETIREMENT, roth.getCategory());

        // Roth reduces net: gross - taxes - fica - roth
        // 100000 - 0 (pretax) - 10000 (fed) - 10000 (state) - 6200 (ss) - 1450 (medicare) - 0 (posttax fixed) - 4000 (roth)
        assertEquals(68350.0, result.getNetAnnual(), 0.01);
    }

    @Test
    void calculate_withRoth401k_shouldComputeOnRegularWagesOnly() {
        input.setAnnualGross(115000.0);
        input.setSalaryAnnual(100000.0);
        input.setBonusAnnual(15000.0);
        Posttax posttax = new Posttax();
        posttax.setRoth401kPercent(0.05);
        input.setPosttax(posttax);

        when(deductionCalculator.calculatePretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculateGenericPretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculatePensionContribution(any(), anyDouble())).thenReturn(0.0);
        when(bracketCalculator.calculateTax(anyDouble(), anyList())).thenReturn(10000.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        calculator.calculate(input, rulePack);

        // Roth is computed on regular wages = 100k (not 115k gross)
        verify(deductionCalculator).calculateRoth401k(posttax, 100000.0);
    }

    // ── W-4 fields (Phase 2) ──────────────────────────────────────────────────────

    @Test
    void calculate_withDependentsAmount_shouldReduceFederalTax() {
        W4 w4 = new W4();
        w4.setDependentsAmount(2000.0);
        input.getUsOptions().setW4(w4);

        when(deductionCalculator.calculatePretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculateGenericPretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculatePensionContribution(any(), anyDouble())).thenReturn(0.0);
        when(bracketCalculator.calculateTax(anyDouble(), anyList())).thenReturn(15000.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        LineItem fed = findLineItem(result, "Federal Income Tax");
        assertNotNull(fed);
        // 15000 from brackets - 2000 dependents credit = 13000
        assertEquals(13000.0, fed.getAmount(), 0.01);
    }

    @Test
    void calculate_withDependentsLargerThanFederalTax_shouldFloorAtZero() {
        W4 w4 = new W4();
        w4.setDependentsAmount(20000.0); // more than bracket tax
        input.getUsOptions().setW4(w4);

        when(deductionCalculator.calculatePretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculateGenericPretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculatePensionContribution(any(), anyDouble())).thenReturn(0.0);
        when(bracketCalculator.calculateTax(anyDouble(), anyList())).thenReturn(15000.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        // Federal cannot go negative — should be omitted (line item only emitted when > 0)
        assertNull(findLineItem(result, "Federal Income Tax"));
    }

    @Test
    void calculate_withOtherIncome_shouldIncreaseTaxableForBrackets() {
        W4 w4 = new W4();
        w4.setOtherIncome(5000.0);
        input.getUsOptions().setW4(w4);

        when(deductionCalculator.calculatePretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculateGenericPretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculatePensionContribution(any(), anyDouble())).thenReturn(0.0);
        when(bracketCalculator.calculateTax(anyDouble(), anyList())).thenReturn(10000.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        calculator.calculate(input, rulePack);

        // Bracket call: regularWages(100000) - pretax(0) + otherIncome(5000) - std(14600) = 90400
        verify(bracketCalculator).calculateTax(eq(90400.0), anyList());
    }

    @Test
    void calculate_withItemizedDeductionsLargerThanStandard_shouldUseItemized() {
        W4 w4 = new W4();
        w4.setItemizedDeductions(20000.0); // larger than $14,600 SINGLE std
        input.getUsOptions().setW4(w4);

        when(deductionCalculator.calculatePretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculateGenericPretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculatePensionContribution(any(), anyDouble())).thenReturn(0.0);
        when(bracketCalculator.calculateTax(anyDouble(), anyList())).thenReturn(10000.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        calculator.calculate(input, rulePack);

        // Itemized overrides standard: 100000 - 0 + 0 - 20000 = 80000
        verify(bracketCalculator).calculateTax(eq(80000.0), anyList());
    }

    @Test
    void calculate_withItemizedSmallerThanStandard_shouldUseStandard() {
        W4 w4 = new W4();
        w4.setItemizedDeductions(5000.0); // smaller than $14,600
        input.getUsOptions().setW4(w4);

        when(deductionCalculator.calculatePretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculateGenericPretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculatePensionContribution(any(), anyDouble())).thenReturn(0.0);
        when(bracketCalculator.calculateTax(anyDouble(), anyList())).thenReturn(10000.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        calculator.calculate(input, rulePack);

        // Standard wins: 100000 - 0 + 0 - 14600 = 85400
        verify(bracketCalculator).calculateTax(eq(85400.0), anyList());
    }

    @Test
    void calculate_withAdditionalWithholding_shouldAddPerPeriodTimesPeriods() {
        input.setPayCadence(PayCadence.BIWEEKLY);
        W4 w4 = new W4();
        w4.setAdditionalWithholding(25.0); // $25 per pay period
        input.getUsOptions().setW4(w4);

        when(deductionCalculator.calculatePretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculateGenericPretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculatePensionContribution(any(), anyDouble())).thenReturn(0.0);
        when(bracketCalculator.calculateTax(anyDouble(), anyList())).thenReturn(10000.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        LineItem fed = findLineItem(result, "Federal Income Tax");
        assertNotNull(fed);
        // 10000 + 25 × 26 = 10650
        assertEquals(10650.0, fed.getAmount(), 0.01);
    }

    // ── Legacy (pre-2020) W-4 allowances ──────────────────────────────────────────

    @Test
    void calculate_withOldW4Allowances_shouldUseAllowanceBasedTaxable() {
        CountryOptionsUS usOptions = input.getUsOptions();
        usOptions.setAllowances(2);
        W4 w4 = new W4();
        w4.setUseOldW4(true);
        usOptions.setW4(w4);

        when(deductionCalculator.calculatePretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculateGenericPretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculatePensionContribution(any(), anyDouble())).thenReturn(0.0);
        when(bracketCalculator.calculateTax(anyDouble(), anyList())).thenReturn(10000.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        calculator.calculate(input, rulePack);

        // Legacy path: 100000 - 0 pretax - (2 × 4300 allowance) = 91400; no standard deduction
        verify(bracketCalculator).calculateTax(eq(91400.0), anyList());
    }

    @Test
    void calculate_withOldW4_shouldIgnoreModernW4FieldsAndDependentsCredit() {
        CountryOptionsUS usOptions = input.getUsOptions();
        usOptions.setAllowances(1);
        W4 w4 = new W4();
        w4.setUseOldW4(true);
        w4.setDependentsAmount(2000.0);     // modern step 3 — must be ignored on old W-4
        w4.setOtherIncome(5000.0);          // modern step 4(a) — must be ignored
        w4.setItemizedDeductions(20000.0);  // modern step 4(b) — must be ignored
        usOptions.setW4(w4);

        when(deductionCalculator.calculatePretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculateGenericPretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculatePensionContribution(any(), anyDouble())).thenReturn(0.0);
        when(bracketCalculator.calculateTax(anyDouble(), anyList())).thenReturn(15000.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        // Legacy taxable = 100000 - (1 × 4300) = 95700. If modern fields had applied it would be
        // 100000 + 5000 otherIncome - 20000 itemized = 85000, so 95700 proves they're ignored.
        verify(bracketCalculator).calculateTax(eq(95700.0), anyList());
        // Dependents credit must not apply on the legacy path → federal stays at bracket result
        LineItem fed = findLineItem(result, "Federal Income Tax");
        assertNotNull(fed);
        assertEquals(15000.0, fed.getAmount(), 0.01);
    }

    // ── W-4 exemption flags (Phase 2) ─────────────────────────────────────────────

    @Test
    void calculate_withExemptFederal_shouldOmitFederalLineItem() {
        W4 w4 = new W4();
        w4.setExemptFederal(true);
        input.getUsOptions().setW4(w4);

        when(deductionCalculator.calculatePretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculateGenericPretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculatePensionContribution(any(), anyDouble())).thenReturn(0.0);
        when(bracketCalculator.calculateTax(anyDouble(), anyList())).thenReturn(10000.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        assertNull(findLineItem(result, "Federal Income Tax"));
        assertTrue(result.getExplanations().stream()
            .anyMatch(e -> "fed_tax_exempt".equals(e.getId())));
    }

    @Test
    void calculate_withExemptSocialSecurity_shouldOmitSSLineItem() {
        W4 w4 = new W4();
        w4.setExemptSocialSecurity(true);
        input.getUsOptions().setW4(w4);

        when(deductionCalculator.calculatePretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculateGenericPretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculatePensionContribution(any(), anyDouble())).thenReturn(0.0);
        when(bracketCalculator.calculateTax(anyDouble(), anyList())).thenReturn(10000.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        assertNull(findLineItem(result, "Social Security"));
        assertNotNull(findLineItem(result, "Medicare")); // Medicare still applies
    }

    @Test
    void calculate_withExemptMedicare_shouldOmitMedicareLineItem() {
        W4 w4 = new W4();
        w4.setExemptMedicare(true);
        input.getUsOptions().setW4(w4);

        when(deductionCalculator.calculatePretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculateGenericPretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculatePensionContribution(any(), anyDouble())).thenReturn(0.0);
        when(bracketCalculator.calculateTax(anyDouble(), anyList())).thenReturn(10000.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        assertNull(findLineItem(result, "Medicare"));
        assertNotNull(findLineItem(result, "Social Security")); // SS still applies
    }

    @Test
    void calculate_withAllExemptions_shouldOmitAllTaxLineItems() {
        W4 w4 = new W4();
        w4.setExemptFederal(true);
        w4.setExemptSocialSecurity(true);
        w4.setExemptMedicare(true);
        input.getUsOptions().setW4(w4);

        when(deductionCalculator.calculatePretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculateGenericPretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculatePensionContribution(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        assertNull(findLineItem(result, "Federal Income Tax"));
        assertNull(findLineItem(result, "Social Security"));
        assertNull(findLineItem(result, "Medicare"));
        // Net = gross when everything is exempt
        assertEquals(100000.0, result.getNetAnnual(), 0.01);
    }

    // ── State line item naming + categories ──────────────────────────────────────

    @Test
    void calculate_stateLineItem_shouldBePrefixedWithStateCode() {
        when(deductionCalculator.calculatePretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculateGenericPretaxDeductions(any(), anyDouble())).thenReturn(0.0);
        when(deductionCalculator.calculatePensionContribution(any(), anyDouble())).thenReturn(0.0);
        when(bracketCalculator.calculateTax(anyDouble(), anyList())).thenReturn(5000.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        LineItem state = findLineItem(result, "CA State Income Tax");
        assertNotNull(state);
        assertEquals(LineItemCategory.TAX_STATE, state.getCategory());
    }

    // ── Categories on every emitted line item ────────────────────────────────────

    @Test
    void calculate_allEmittedLineItems_shouldHaveCategory() {
        Pretax pretax = new Pretax();
        pretax.setMedical(2000.0);
        pretax.setHsa(3000.0);
        pretax.setPensionPercent(0.05);
        input.setPretax(pretax);
        input.setSalaryAnnual(100000.0);
        Posttax posttax = new Posttax();
        posttax.setRoth401kPercent(0.02);
        input.setPosttax(posttax);

        when(deductionCalculator.calculatePretaxDeductions(any(), anyDouble())).thenReturn(10000.0);
        when(deductionCalculator.calculateGenericPretaxDeductions(any(), anyDouble())).thenReturn(3000.0);
        when(deductionCalculator.calculatePensionContribution(any(), anyDouble())).thenReturn(5000.0);
        when(deductionCalculator.calculateRoth401k(any(), anyDouble())).thenReturn(2000.0);
        when(bracketCalculator.calculateTax(anyDouble(), anyList())).thenReturn(10000.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(100.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        for (LineItem item : result.getLineItems()) {
            assertNotNull(item.getCategory(),
                "Line item '" + item.getName() + "' should be tagged with a category");
        }
    }

    // ── Supplemental income slice (bonus + commission + RSU vesting) ─────────────

    @Test
    void calculate_withBonusAndRsu_shouldBreakOutSupplementalSlice() {
        // regular 100k + bonus 10k + RSU 20k = 130k gross, all under the SS wage base
        input.setAnnualGross(130000.0);
        input.setBonusAnnual(10000.0);
        input.setRsuVestingAnnual(20000.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        var supplemental = result.getSupplemental();
        assertNotNull(supplemental);
        assertEquals(10000.0, supplemental.getBonusGross(), 0.01);
        assertEquals(0.0, supplemental.getCommissionGross(), 0.01);
        assertEquals(20000.0, supplemental.getRsuGross(), 0.01);
        assertEquals(30000.0 * 0.22, supplemental.getFederalTax(), 0.01);
        assertEquals(30000.0 * 0.062, supplemental.getSocialSecurity(), 0.01);
        assertEquals(30000.0 * 0.0145, supplemental.getMedicare(), 0.01);
        assertEquals(30000.0 - 6600.0 - 1860.0 - 435.0, supplemental.getNet(), 0.01);

        LineItem rsuItem = findLineItem(result, "RSU Vesting");
        assertNotNull(rsuItem);
        assertEquals(20000.0, rsuItem.getAmount(), 0.01);
        assertEquals(LineItemCategory.EARNINGS, rsuItem.getCategory());
    }

    @Test
    void calculate_supplementalStraddlingSsWageBase_shouldCapSocialSecuritySlice() {
        // regular 160k fills most of the 168.6k base; only 8.6k of the 20k bonus is SS-taxable
        input.setAnnualGross(180000.0);
        input.setBonusAnnual(20000.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        assertEquals(8600.0 * 0.062, result.getSupplemental().getSocialSecurity(), 0.01);
        // whole-calculation SS still caps at the wage base
        assertEquals(168600.0 * 0.062, findLineItem(result, "Social Security").getAmount(), 0.01);
    }

    @Test
    void calculate_regularWagesAboveSsWageBase_shouldZeroSsSliceAndAddMedicareSurtax() {
        // regular 200k already exceeds the SS base and sits exactly at the Medicare threshold,
        // so the whole 20k bonus escapes SS but takes the full 0.9% Additional Medicare
        input.setAnnualGross(220000.0);
        input.setBonusAnnual(20000.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        var supplemental = result.getSupplemental();
        assertEquals(0.0, supplemental.getSocialSecurity(), 0.01);
        assertEquals(20000.0 * 0.0145 + 20000.0 * 0.009, supplemental.getMedicare(), 0.01);
    }

    @Test
    void calculate_supplementalWithFicaExemptions_shouldZeroExemptSlices() {
        input.setAnnualGross(110000.0);
        input.setBonusAnnual(10000.0);
        W4 w4 = new W4();
        w4.setExemptSocialSecurity(true);
        w4.setExemptMedicare(true);
        input.getUsOptions().setW4(w4);

        CalculationResult result = calculator.calculate(input, rulePack);

        var supplemental = result.getSupplemental();
        assertEquals(0.0, supplemental.getSocialSecurity(), 0.01);
        assertEquals(0.0, supplemental.getMedicare(), 0.01);
        assertEquals(2200.0, supplemental.getFederalTax(), 0.01);
        assertEquals(7800.0, supplemental.getNet(), 0.01);
    }

    @Test
    void calculate_withoutSupplementalIncome_shouldOmitSupplementalBlock() {
        CalculationResult result = calculator.calculate(input, rulePack);

        assertNull(result.getSupplemental());
    }

    @Test
    void calculate_bonusDeferredToFutureYear_shouldExplainExclusion() {
        input.setBonusDeferredToYear(2027);

        CalculationResult result = calculator.calculate(input, rulePack);

        assertNull(result.getSupplemental());
        assertTrue(result.getExplanations().stream()
                .anyMatch(e -> "bonus_deferred".equals(e.getId())
                        && e.getText().contains("2027")));
    }

    // ── Helper ────────────────────────────────────────────────────────────────────

    private LineItem findLineItem(CalculationResult result, String name) {
        return result.getLineItems().stream()
            .filter(i -> name.equals(i.getName()))
            .findFirst().orElse(null);
    }
}
