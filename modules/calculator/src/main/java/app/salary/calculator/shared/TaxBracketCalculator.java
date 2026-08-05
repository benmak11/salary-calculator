package app.salary.calculator.shared;

import app.salary.rules.RulePack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Marginal-rate tax over a list of brackets.
 *
 * <p>Brackets are expressed as lower bounds ({@link RulePack.TaxBracket#getOver()}): a rate
 * applies to income above its own bound and below the next bracket's, and the last bracket is
 * unbounded. This mirrors how tax authorities publish their tables.
 *
 * <p>Packs written against the older upper-bound (<em>upTo</em>) schema are normalized on
 * read, so a remote pack predating the change still computes correctly.
 */
public class TaxBracketCalculator {

    /**
     * Lower bound of each bracket, resolving the legacy {@code upTo} form where needed.
     *
     * <p>Under {@code upTo} a bracket starts wherever the previous one ended, and the first
     * starts at zero, so the two schemas describe identical bands and convert exactly.
     */
    private static List<Double> lowerBounds(List<RulePack.TaxBracket> brackets) {
        List<Double> bounds = new ArrayList<>(brackets.size());
        for (int i = 0; i < brackets.size(); i++) {
            Double over = brackets.get(i).getOver();
            if (over != null) {
                bounds.add(over);
            } else if (i == 0) {
                bounds.add(0.0);
            } else {
                Double previousUpTo = brackets.get(i - 1).getUpTo();
                bounds.add(previousUpTo != null ? previousUpTo : bounds.get(i - 1));
            }
        }
        return bounds;
    }

    /** Upper bound of bracket {@code i}: the next bracket's lower bound, or unbounded. */
    private static double upperBound(List<Double> bounds, int i) {
        return i + 1 < bounds.size() ? bounds.get(i + 1) : Double.POSITIVE_INFINITY;
    }

    public double calculateTax(double income, List<RulePack.TaxBracket> brackets) {
        List<Double> bounds = lowerBounds(brackets);
        double tax = 0.0;

        for (int i = 0; i < brackets.size(); i++) {
            double from = bounds.get(i);
            if (income <= from) {
                break;
            }
            double taxableInBand = Math.min(income, upperBound(bounds, i)) - from;
            if (taxableInBand > 0) {
                tax += taxableInBand * brackets.get(i).getRate();
            }
        }
        return tax;
    }

    public TaxBreakdown calculateTaxWithBreakdown(double income, List<RulePack.TaxBracket> brackets) {
        TaxBreakdown breakdown = new TaxBreakdown();
        List<Double> bounds = lowerBounds(brackets);

        for (int i = 0; i < brackets.size(); i++) {
            double from = bounds.get(i);
            if (income <= from) {
                break;
            }
            double taxableInBand = Math.min(income, upperBound(bounds, i)) - from;
            if (taxableInBand > 0) {
                double rate = brackets.get(i).getRate();
                breakdown.addBand(i, taxableInBand, rate, taxableInBand * rate);
            }
        }
        return breakdown;
    }

    public static class TaxBreakdown {
        private double totalTax = 0.0;
        private final Map<Integer, BandDetail> bands = new HashMap<>();

        public void addBand(int bandIndex, double income, double rate, double tax) {
            bands.put(bandIndex, new BandDetail(income, rate, tax));
            totalTax += tax;
        }

        public double getTotalTax() { return totalTax; }
        public Map<Integer, BandDetail> getBands() { return bands; }

        public static class BandDetail {
            private final double income;
            private final double rate;
            private final double tax;

            public BandDetail(double income, double rate, double tax) {
                this.income = income;
                this.rate = rate;
                this.tax = tax;
            }

            public double getIncome() { return income; }
            public double getRate() { return rate; }
            public double getTax() { return tax; }
        }
    }
}
