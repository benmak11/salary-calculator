package app.salary.calculator.countries;

import app.salary.calculator.engine.CalculationInput;
import app.salary.calculator.engine.CalculationResult;
import app.salary.calculator.shared.DeductionCalculator;
import app.salary.calculator.shared.StudentLoanCalculator;
import app.salary.calculator.shared.TaxBracketCalculator;
import app.salary.common.constants.Country;
import app.salary.common.constants.StudentLoanPlan;
import app.salary.common.dto.CountryOptionsUK;
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
class UKCalculatorTest {

    @Mock
    private TaxBracketCalculator bracketCalculator;

    @Mock
    private DeductionCalculator deductionCalculator;

    @Mock
    private StudentLoanCalculator studentLoanCalculator;

    @InjectMocks
    private UKCalculator calculator;

    private RulePack rulePack;
    private CalculationInput input;

    @BeforeEach
    void setUp() {
        // Setup RulePack
        rulePack = new RulePack();

        RulePack.Metadata metadata = new RulePack.Metadata();
        metadata.setVersion("UK-2025.4.0");
        rulePack.setMetadata(metadata);

        RulePack.IncomeTax incomeTax = new RulePack.IncomeTax();
        incomeTax.setPersonalAllowance(12570.0);
        incomeTax.setTaperStart(100000.0);
        incomeTax.setTaperRate(0.5);

        List<RulePack.TaxBracket> bands = new ArrayList<>();
        RulePack.TaxBracket band1 = new RulePack.TaxBracket();
        band1.setUpTo(37700.0);
        band1.setRate(0.20);
        bands.add(band1);
        incomeTax.setBands(bands);
        rulePack.setIncomeTax(incomeTax);

        RulePack.NationalInsurance ni = new RulePack.NationalInsurance();
        ni.setPrimaryThresholdAnnual(12570.0);
        ni.setUpperEarningsLimit(50270.0);
        ni.setMainRate(0.08);
        ni.setUpperRate(0.02);
        rulePack.setNi(ni);

        // Setup input
        input = new CalculationInput();
        input.setAnnualGross(50000.0);
        input.setPretax(new Pretax());
        input.setPosttax(new Posttax());

        CountryOptionsUK ukOptions = new CountryOptionsUK();
        ukOptions.setTaxCode("1257L");
        input.setUkOptions(ukOptions);
    }

    @Test
    void supports_ukCountryAndTaxYear2025_shouldReturnTrue() {
        assertTrue(calculator.supports(Country.UK, 2025));
    }

    @Test
    void supports_ukCountryAndTaxYear2026_shouldReturnTrue() {
        assertTrue(calculator.supports(Country.UK, 2026));
    }

    @Test
    void supports_ukCountryAndTaxYear2024_shouldReturnFalse() {
        assertFalse(calculator.supports(Country.UK, 2024));
    }

    @Test
    void supports_usCountry_shouldReturnFalse() {
        assertFalse(calculator.supports(Country.US, 2025));
    }

    @Test
    void calculate_shouldUseAnnualGrossDirectly() {
        when(deductionCalculator.calculatePensionContribution(any(), eq(50000.0))).thenReturn(2500.0);
        when(bracketCalculator.calculateTaxWithBreakdown(anyDouble(), anyList()))
            .thenReturn(createTaxBreakdown(7486.0));
        when(studentLoanCalculator.calculateRepayment(any(), anyDouble(), any())).thenReturn(0.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        assertNotNull(result);
        assertEquals(50000.0, result.getGrossAnnual());
        assertEquals("GBP", result.getCurrency());
        assertEquals("UK-2025.4.0", result.getRulePackVersion());

        // Verify annualGross was used directly, not via Income calculation
        verify(deductionCalculator).calculatePensionContribution(any(), eq(50000.0));
    }

    @Test
    void calculate_withNoPension_shouldNotAddPensionLineItem() {
        when(deductionCalculator.calculatePensionContribution(any(), anyDouble())).thenReturn(0.0);
        when(bracketCalculator.calculateTaxWithBreakdown(anyDouble(), anyList()))
            .thenReturn(createTaxBreakdown(7486.0));
        when(studentLoanCalculator.calculateRepayment(any(), anyDouble(), any())).thenReturn(0.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        boolean hasPensionLineItem = result.getLineItems().stream()
            .anyMatch(item -> item.getName().equals("Employee Pension Contribution"));
        assertFalse(hasPensionLineItem);
    }

    @Test
    void calculate_withPension_shouldAddPensionExplanation() {
        Pretax pretax = new Pretax();
        pretax.setPensionPercent(0.05);
        input.setPretax(pretax);

        when(deductionCalculator.calculatePensionContribution(any(), anyDouble())).thenReturn(2500.0);
        when(bracketCalculator.calculateTaxWithBreakdown(anyDouble(), anyList()))
            .thenReturn(createTaxBreakdown(7486.0));
        when(studentLoanCalculator.calculateRepayment(any(), anyDouble(), any())).thenReturn(0.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        boolean hasPensionExplanation = result.getExplanations().stream()
            .anyMatch(exp -> exp.getId().equals("pension_contribution"));
        assertTrue(hasPensionExplanation);
    }

    @Test
    void calculate_withStudentLoan_shouldAddStudentLoanLineItem() {
        Posttax posttax = new Posttax();
        posttax.setStudentLoanPlan(StudentLoanPlan.PLAN2);
        input.setPosttax(posttax);

        when(deductionCalculator.calculatePensionContribution(any(), anyDouble())).thenReturn(0.0);
        when(bracketCalculator.calculateTaxWithBreakdown(anyDouble(), anyList()))
            .thenReturn(createTaxBreakdown(7486.0));
        when(studentLoanCalculator.calculateRepayment(eq(StudentLoanPlan.PLAN2), anyDouble(), any()))
            .thenReturn(500.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        boolean hasStudentLoanLineItem = result.getLineItems().stream()
            .anyMatch(item -> item.getName().contains("Student Loan") && item.getName().contains("PLAN2"));
        assertTrue(hasStudentLoanLineItem);
    }

    @Test
    void calculate_withNoStudentLoan_shouldNotAddStudentLoanLineItem() {
        when(deductionCalculator.calculatePensionContribution(any(), anyDouble())).thenReturn(0.0);
        when(bracketCalculator.calculateTaxWithBreakdown(anyDouble(), anyList()))
            .thenReturn(createTaxBreakdown(7486.0));
        when(studentLoanCalculator.calculateRepayment(isNull(), anyDouble(), any())).thenReturn(0.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        boolean hasStudentLoanLineItem = result.getLineItems().stream()
            .anyMatch(item -> item.getName().contains("Student Loan"));
        assertFalse(hasStudentLoanLineItem);
    }

    @Test
    void calculate_withPosttaxDeductions_shouldAddLineItem() {
        when(deductionCalculator.calculatePensionContribution(any(), anyDouble())).thenReturn(0.0);
        when(bracketCalculator.calculateTaxWithBreakdown(anyDouble(), anyList()))
            .thenReturn(createTaxBreakdown(7486.0));
        when(studentLoanCalculator.calculateRepayment(any(), anyDouble(), any())).thenReturn(0.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(100.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        boolean hasPosttaxLineItem = result.getLineItems().stream()
            .anyMatch(item -> item.getName().equals("Other Post-tax Deductions"));
        assertTrue(hasPosttaxLineItem);
    }

    @Test
    void calculate_withIncomeAboveTaperStart_shouldReducePersonalAllowance() {
        input.setAnnualGross(120000.0);

        when(deductionCalculator.calculatePensionContribution(any(), anyDouble())).thenReturn(0.0);
        when(bracketCalculator.calculateTaxWithBreakdown(anyDouble(), anyList()))
            .thenReturn(createTaxBreakdown(20000.0));
        when(studentLoanCalculator.calculateRepayment(any(), anyDouble(), any())).thenReturn(0.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        boolean hasTaperExplanation = result.getExplanations().stream()
            .anyMatch(exp -> exp.getId().equals("personal_allowance_taper"));
        assertTrue(hasTaperExplanation);
    }

    @Test
    void calculate_withIncomeBelowTaperStart_shouldUseFullPersonalAllowance() {
        input.setAnnualGross(60000.0);

        when(deductionCalculator.calculatePensionContribution(any(), anyDouble())).thenReturn(0.0);
        when(bracketCalculator.calculateTaxWithBreakdown(anyDouble(), anyList()))
            .thenReturn(createTaxBreakdown(10000.0));
        when(studentLoanCalculator.calculateRepayment(any(), anyDouble(), any())).thenReturn(0.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        boolean hasPersonalAllowanceExplanation = result.getExplanations().stream()
            .anyMatch(exp -> exp.getId().equals("personal_allowance") &&
                             exp.getText().contains("Full personal allowance"));
        assertTrue(hasPersonalAllowanceExplanation);
    }

    @Test
    void calculate_withNullUkOptions_shouldUseDefaultTaxCode() {
        input.setUkOptions(null);

        when(deductionCalculator.calculatePensionContribution(any(), anyDouble())).thenReturn(0.0);
        when(bracketCalculator.calculateTaxWithBreakdown(anyDouble(), anyList()))
            .thenReturn(createTaxBreakdown(7486.0));
        when(studentLoanCalculator.calculateRepayment(any(), anyDouble(), any())).thenReturn(0.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        boolean hasTaxCodeExplanation = result.getExplanations().stream()
            .anyMatch(exp -> exp.getId().equals("tax_code") &&
                             exp.getText().contains("1257L"));
        assertTrue(hasTaxCodeExplanation);
    }

    @Test
    void calculate_withCustomTaxCode_shouldUseTaxCodeInExplanation() {
        CountryOptionsUK ukOptions = new CountryOptionsUK();
        ukOptions.setTaxCode("1100L");
        input.setUkOptions(ukOptions);

        when(deductionCalculator.calculatePensionContribution(any(), anyDouble())).thenReturn(0.0);
        when(bracketCalculator.calculateTaxWithBreakdown(anyDouble(), anyList()))
            .thenReturn(createTaxBreakdown(7486.0));
        when(studentLoanCalculator.calculateRepayment(any(), anyDouble(), any())).thenReturn(0.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        boolean hasTaxCodeExplanation = result.getExplanations().stream()
            .anyMatch(exp -> exp.getId().equals("tax_code") &&
                             exp.getText().contains("1100L"));
        assertTrue(hasTaxCodeExplanation);
    }

    @Test
    void calculate_withIncomeAboveUpperEarningsLimit_shouldCalculateUpperRateNI() {
        input.setAnnualGross(60000.0);

        when(deductionCalculator.calculatePensionContribution(any(), anyDouble())).thenReturn(0.0);
        when(bracketCalculator.calculateTaxWithBreakdown(anyDouble(), anyList()))
            .thenReturn(createTaxBreakdown(10000.0));
        when(studentLoanCalculator.calculateRepayment(any(), anyDouble(), any())).thenReturn(0.0);
        when(deductionCalculator.calculatePosttaxDeductions(any())).thenReturn(0.0);

        CalculationResult result = calculator.calculate(input, rulePack);

        // Should have both main rate and upper rate NI
        boolean hasUpperRateNI = result.getLineItems().stream()
            .anyMatch(item -> item.getName().contains("Upper Rate 2%"));
        assertTrue(hasUpperRateNI);

        boolean hasUpperRateExplanation = result.getExplanations().stream()
            .anyMatch(exp -> exp.getId().equals("ni_upper_rate"));
        assertTrue(hasUpperRateExplanation);
    }

    @Test
    void getCountryCode_shouldReturnUK() {
        assertEquals("UK", calculator.getCountryCode());
    }

    private TaxBracketCalculator.TaxBreakdown createTaxBreakdown(double totalTax) {
        TaxBracketCalculator.TaxBreakdown breakdown = new TaxBracketCalculator.TaxBreakdown();
        // Add a band to the breakdown which automatically updates totalTax
        breakdown.addBand(0, totalTax / 0.20, 0.20, totalTax);
        return breakdown;
    }
}
