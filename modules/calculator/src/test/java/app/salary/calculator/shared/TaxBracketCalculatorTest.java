package app.salary.calculator.shared;

import app.salary.rules.RulePack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

    @ParameterizedTest(name = "income {0} -> tax {1}")
    @CsvSource({
            "37430.0,  7486.0", // all within basic band: 37430 * 20%
            "50000.0, 12460.0", // spans basic + higher: 37700 * 20% + 12300 * 40%
            "0.0,          0.0", // zero income
            "37700.0,  7540.0", // exactly at basic-band limit: 37700 * 20%
    })
    void calculateTax_withUKStyleBrackets_shouldCalculateProgressively(double income, double expectedTax) {
        double tax = calculator.calculateTax(income, createUKStyleBrackets());

        assertEquals(expectedTax, tax, 0.01);
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
