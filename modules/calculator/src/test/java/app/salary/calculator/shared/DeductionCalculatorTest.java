package app.salary.calculator.shared;

import app.salary.common.dto.Posttax;
import app.salary.common.dto.Pretax;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeductionCalculatorTest {

    private DeductionCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new DeductionCalculator();
    }

    // Tests for calculatePretaxDeductions

    @Test
    void calculatePretaxDeductions_withNullPretax_shouldReturnZero() {
        double result = calculator.calculatePretaxDeductions(null, 50000.0);
        assertEquals(0.0, result, 0.01);
    }

    @Test
    void calculatePretaxDeductions_withEmptyPretax_shouldReturnZero() {
        Pretax pretax = new Pretax();
        double result = calculator.calculatePretaxDeductions(pretax, 50000.0);
        assertEquals(0.0, result, 0.01);
    }

    @Test
    void calculatePretaxDeductions_withPercentOnly_shouldCalculateCorrectly() {
        Pretax pretax = new Pretax();
        pretax.setPercent(0.10); // 10%

        double result = calculator.calculatePretaxDeductions(pretax, 50000.0);

        assertEquals(5000.0, result, 0.01);
    }

    @Test
    void calculatePretaxDeductions_withFixedOnly_shouldReturnFixedAmount() {
        Pretax pretax = new Pretax();
        pretax.setFixed(2000.0);

        double result = calculator.calculatePretaxDeductions(pretax, 50000.0);

        assertEquals(2000.0, result, 0.01);
    }

    @Test
    void calculatePretaxDeductions_withHsaOnly_shouldReturnHsaAmount() {
        Pretax pretax = new Pretax();
        pretax.setHsa(3000.0);

        double result = calculator.calculatePretaxDeductions(pretax, 50000.0);

        assertEquals(3000.0, result, 0.01);
    }

    @Test
    void calculatePretaxDeductions_withPensionPercentOnly_shouldCalculateCorrectly() {
        Pretax pretax = new Pretax();
        pretax.setPensionPercent(0.05); // 5%

        double result = calculator.calculatePretaxDeductions(pretax, 50000.0);

        assertEquals(2500.0, result, 0.01);
    }

    @Test
    void calculatePretaxDeductions_withAllDeductions_shouldSumAll() {
        Pretax pretax = new Pretax();
        pretax.setPercent(0.10);        // 5000
        pretax.setFixed(1000.0);         // 1000
        pretax.setHsa(2000.0);           // 2000
        pretax.setPensionPercent(0.05); // 2500

        double result = calculator.calculatePretaxDeductions(pretax, 50000.0);

        assertEquals(10500.0, result, 0.01);
    }

    @Test
    void calculatePretaxDeductions_withZeroValues_shouldReturnZero() {
        Pretax pretax = new Pretax();
        pretax.setPercent(0.0);
        pretax.setFixed(0.0);
        pretax.setHsa(0.0);
        pretax.setPensionPercent(0.0);

        double result = calculator.calculatePretaxDeductions(pretax, 50000.0);

        assertEquals(0.0, result, 0.01);
    }

    // Tests for calculatePosttaxDeductions

    @Test
    void calculatePosttaxDeductions_withNullPosttax_shouldReturnZero() {
        double result = calculator.calculatePosttaxDeductions(null);
        assertEquals(0.0, result, 0.01);
    }

    @Test
    void calculatePosttaxDeductions_withNullFixed_shouldReturnZero() {
        Posttax posttax = new Posttax();
        double result = calculator.calculatePosttaxDeductions(posttax);
        assertEquals(0.0, result, 0.01);
    }

    @Test
    void calculatePosttaxDeductions_withFixedAmount_shouldReturnFixedAmount() {
        Posttax posttax = new Posttax();
        posttax.setFixed(500.0);

        double result = calculator.calculatePosttaxDeductions(posttax);

        assertEquals(500.0, result, 0.01);
    }

    // Tests for calculatePensionContribution

    @Test
    void calculatePensionContribution_withNullPretax_shouldReturnZero() {
        double result = calculator.calculatePensionContribution(null, 50000.0);
        assertEquals(0.0, result, 0.01);
    }

    @Test
    void calculatePensionContribution_withNullPensionPercent_shouldReturnZero() {
        Pretax pretax = new Pretax();
        double result = calculator.calculatePensionContribution(pretax, 50000.0);
        assertEquals(0.0, result, 0.01);
    }

    @Test
    void calculatePensionContribution_withZeroPensionPercent_shouldReturnZero() {
        Pretax pretax = new Pretax();
        pretax.setPensionPercent(0.0);

        double result = calculator.calculatePensionContribution(pretax, 50000.0);

        assertEquals(0.0, result, 0.01);
    }

    @Test
    void calculatePensionContribution_withValidPensionPercent_shouldCalculateCorrectly() {
        Pretax pretax = new Pretax();
        pretax.setPensionPercent(0.05); // 5%

        double result = calculator.calculatePensionContribution(pretax, 50000.0);

        assertEquals(2500.0, result, 0.01);
    }

    @Test
    void calculatePensionContribution_withHighPensionPercent_shouldCalculateCorrectly() {
        Pretax pretax = new Pretax();
        pretax.setPensionPercent(0.15); // 15%

        double result = calculator.calculatePensionContribution(pretax, 100000.0);

        assertEquals(15000.0, result, 0.01);
    }
}
