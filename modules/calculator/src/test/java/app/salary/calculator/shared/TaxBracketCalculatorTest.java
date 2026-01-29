package app.salary.calculator.shared;

import app.salary.rules.RulePack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TaxBracketCalculatorTest {

    private TaxBracketCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new TaxBracketCalculator();
    }

    @Test
    void calculateTax_withSingleBracket_shouldCalculateCorrectly() {
        List<RulePack.TaxBracket> brackets = new ArrayList<>();
        RulePack.TaxBracket bracket = new RulePack.TaxBracket();
        bracket.setUpTo(null); // Unlimited
        bracket.setRate(0.20);
        brackets.add(bracket);

        double tax = calculator.calculateTax(50000.0, brackets);

        assertEquals(10000.0, tax, 0.01);
    }

    @Test
    void calculateTax_withMultipleBrackets_shouldCalculateProgressively() {
        List<RulePack.TaxBracket> brackets = createUKStyleBrackets();

        // Income of £50,000 after personal allowance (£37,430 taxable)
        // £37,700 at 20% = £7,540
        // £12,300 at 40% (50000 - 37700) = £4,920
        // But with £37,430 taxable: first £37,430 at 20% = £7,486
        double tax = calculator.calculateTax(37430.0, brackets);

        assertEquals(7486.0, tax, 0.01);
    }

    @Test
    void calculateTax_withIncomeInSecondBracket_shouldCalculateBothBrackets() {
        List<RulePack.TaxBracket> brackets = createUKStyleBrackets();

        // Income spans two brackets: 50000
        // First 37700 at 20% = 7540
        // Remaining 12300 at 40% = 4920
        // Total = 12460
        double tax = calculator.calculateTax(50000.0, brackets);

        assertEquals(12460.0, tax, 0.01);
    }

    @Test
    void calculateTax_withZeroIncome_shouldReturnZero() {
        List<RulePack.TaxBracket> brackets = createUKStyleBrackets();

        double tax = calculator.calculateTax(0.0, brackets);

        assertEquals(0.0, tax, 0.01);
    }

    @Test
    void calculateTax_withIncomeExactlyAtBracketLimit_shouldOnlyUseFirstBracket() {
        List<RulePack.TaxBracket> brackets = createUKStyleBrackets();

        double tax = calculator.calculateTax(37700.0, brackets);

        assertEquals(7540.0, tax, 0.01); // 37700 * 0.20
    }

    @Test
    void calculateTaxWithBreakdown_shouldReturnCorrectTotalTax() {
        List<RulePack.TaxBracket> brackets = createUKStyleBrackets();

        TaxBracketCalculator.TaxBreakdown breakdown = calculator.calculateTaxWithBreakdown(50000.0, brackets);

        assertEquals(12460.0, breakdown.getTotalTax(), 0.01);
    }

    @Test
    void calculateTaxWithBreakdown_shouldReturnCorrectBandDetails() {
        List<RulePack.TaxBracket> brackets = createUKStyleBrackets();

        TaxBracketCalculator.TaxBreakdown breakdown = calculator.calculateTaxWithBreakdown(50000.0, brackets);

        assertEquals(2, breakdown.getBands().size());

        // First band
        TaxBracketCalculator.TaxBreakdown.BandDetail band0 = breakdown.getBands().get(0);
        assertEquals(37700.0, band0.getIncome(), 0.01);
        assertEquals(0.20, band0.getRate(), 0.01);
        assertEquals(7540.0, band0.getTax(), 0.01);

        // Second band
        TaxBracketCalculator.TaxBreakdown.BandDetail band1 = breakdown.getBands().get(1);
        assertEquals(12300.0, band1.getIncome(), 0.01);
        assertEquals(0.40, band1.getRate(), 0.01);
        assertEquals(4920.0, band1.getTax(), 0.01);
    }

    @Test
    void calculateTaxWithBreakdown_withZeroIncome_shouldReturnEmptyBands() {
        List<RulePack.TaxBracket> brackets = createUKStyleBrackets();

        TaxBracketCalculator.TaxBreakdown breakdown = calculator.calculateTaxWithBreakdown(0.0, brackets);

        assertEquals(0.0, breakdown.getTotalTax(), 0.01);
        assertTrue(breakdown.getBands().isEmpty());
    }

    @Test
    void calculateTaxWithBreakdown_withUnlimitedTopBracket_shouldCalculateCorrectly() {
        List<RulePack.TaxBracket> brackets = createUKStyleBrackets();

        // Income of 200000 should hit all brackets including unlimited top bracket
        TaxBracketCalculator.TaxBreakdown breakdown = calculator.calculateTaxWithBreakdown(200000.0, brackets);

        // 37700 at 20% = 7540
        // 87570 (125270-37700) at 40% = 35028
        // 74730 (200000-125270) at 45% = 33628.50
        // Total = 76196.50
        assertEquals(76196.50, breakdown.getTotalTax(), 0.01);
    }

    @Test
    void taxBreakdown_addBand_shouldUpdateTotalTax() {
        TaxBracketCalculator.TaxBreakdown breakdown = new TaxBracketCalculator.TaxBreakdown();

        breakdown.addBand(0, 10000.0, 0.20, 2000.0);
        assertEquals(2000.0, breakdown.getTotalTax(), 0.01);

        breakdown.addBand(1, 5000.0, 0.40, 2000.0);
        assertEquals(4000.0, breakdown.getTotalTax(), 0.01);
    }

    @Test
    void bandDetail_shouldStoreCorrectValues() {
        TaxBracketCalculator.TaxBreakdown.BandDetail detail =
                new TaxBracketCalculator.TaxBreakdown.BandDetail(10000.0, 0.25, 2500.0);

        assertEquals(10000.0, detail.getIncome(), 0.01);
        assertEquals(0.25, detail.getRate(), 0.01);
        assertEquals(2500.0, detail.getTax(), 0.01);
    }

    private List<RulePack.TaxBracket> createUKStyleBrackets() {
        List<RulePack.TaxBracket> brackets = new ArrayList<>();

        RulePack.TaxBracket basic = new RulePack.TaxBracket();
        basic.setUpTo(37700.0);
        basic.setRate(0.20);
        brackets.add(basic);

        RulePack.TaxBracket higher = new RulePack.TaxBracket();
        higher.setUpTo(125270.0);
        higher.setRate(0.40);
        brackets.add(higher);

        RulePack.TaxBracket additional = new RulePack.TaxBracket();
        additional.setUpTo(null); // Unlimited
        additional.setRate(0.45);
        brackets.add(additional);

        return brackets;
    }
}
