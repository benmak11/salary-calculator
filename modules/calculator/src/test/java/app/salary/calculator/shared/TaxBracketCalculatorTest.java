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
        bracket.setOver(0.0); // single band covering all income
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

    // ── legacy upper-bound schema ────────────────────────────────────────────
    // Rule packs published to the rule-pack-service before the `over` migration
    // still carry `upTo`. The mapper behind HttpRulePackClient disables
    // FAIL_ON_UNKNOWN_PROPERTIES, so an unrecognised field would be dropped
    // silently rather than raising — these guard that the legacy form keeps
    // computing identical numbers until every stored pack is republished.

    private static RulePack.TaxBracket over(Double over, double rate) {
        RulePack.TaxBracket b = new RulePack.TaxBracket();
        b.setOver(over);
        b.setRate(rate);
        return b;
    }

    @SuppressWarnings("deprecation")
    private static RulePack.TaxBracket upTo(Double upTo, double rate) {
        RulePack.TaxBracket b = new RulePack.TaxBracket();
        b.setUpTo(upTo);
        b.setRate(rate);
        return b;
    }

    @Test
    void calculateTax_legacyUpToSchema_shouldMatchOverSchema() {
        // Same three bands expressed both ways.
        List<RulePack.TaxBracket> legacy = List.of(
                upTo(12400.0, 0.10), upTo(50400.0, 0.12), upTo(null, 0.22));
        List<RulePack.TaxBracket> modern = List.of(
                over(0.0, 0.10), over(12400.0, 0.12), over(50400.0, 0.22));

        for (double income : new double[]{0, 1, 12400, 12401, 50400, 75000, 250000}) {
            assertEquals(calculator.calculateTax(income, modern),
                    calculator.calculateTax(income, legacy), 0.0001,
                    "schemas disagree at income " + income);
        }
    }

    @Test
    void calculateTaxWithBreakdown_legacyUpToSchema_shouldMatchOverSchema() {
        List<RulePack.TaxBracket> legacy = List.of(
                upTo(12400.0, 0.10), upTo(50400.0, 0.12), upTo(null, 0.22));
        List<RulePack.TaxBracket> modern = List.of(
                over(0.0, 0.10), over(12400.0, 0.12), over(50400.0, 0.22));

        var fromLegacy = calculator.calculateTaxWithBreakdown(75000.0, legacy);
        var fromModern = calculator.calculateTaxWithBreakdown(75000.0, modern);

        assertEquals(fromModern.getTotalTax(), fromLegacy.getTotalTax(), 0.0001);
        assertEquals(fromModern.getBands().size(), fromLegacy.getBands().size());
    }

    @Test
    void calculateTax_incomeBelowFirstThreshold_shouldTaxOnlyTheLowestBand() {
        List<RulePack.TaxBracket> brackets = List.of(
                over(0.0, 0.10), over(12400.0, 0.12));

        assertEquals(1000.0, calculator.calculateTax(10000.0, brackets), 0.0001);
    }

    @Test
    void calculateTax_zeroRatedFirstBand_shouldExemptIncomeBelowThreshold() {
        // Ohio's 2026 shape: nothing under $26,050, 2.75% above it.
        List<RulePack.TaxBracket> brackets = List.of(
                over(0.0, 0.0), over(26050.0, 0.0275));

        assertEquals(0.0, calculator.calculateTax(26050.0, brackets), 0.0001);
        assertEquals(2033.625, calculator.calculateTax(100000.0, brackets), 0.0001);
    }

    private List<RulePack.TaxBracket> createUKStyleBrackets() {
        List<RulePack.TaxBracket> brackets = new ArrayList<>();

        RulePack.TaxBracket basic = new RulePack.TaxBracket();
        basic.setOver(0.0);
        basic.setRate(0.20);
        brackets.add(basic);

        RulePack.TaxBracket higher = new RulePack.TaxBracket();
        higher.setOver(37700.0);
        higher.setRate(0.40);
        brackets.add(higher);

        RulePack.TaxBracket additional = new RulePack.TaxBracket();
        additional.setOver(125270.0);
        additional.setRate(0.45);
        brackets.add(additional);

        return brackets;
    }
}
