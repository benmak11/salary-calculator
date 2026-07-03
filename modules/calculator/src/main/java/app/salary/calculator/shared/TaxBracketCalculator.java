package app.salary.calculator.shared;

import app.salary.rules.RulePack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TaxBracketCalculator {
    public double calculateTax(double income, List<RulePack.TaxBracket> brackets) {
        double tax = 0.0;
        double previousThreshold = 0.0;

        for (RulePack.TaxBracket bracket : brackets) {
            if (bracket.getUpTo() == null || income <= bracket.getUpTo()) {
                tax += (income - previousThreshold) * bracket.getRate();
                break;
            }
            tax += (bracket.getUpTo() - previousThreshold) * bracket.getRate();
            previousThreshold = bracket.getUpTo();
        }
        return tax;
    }

    public TaxBreakdown calculateTaxWithBreakdown(double income, List<RulePack.TaxBracket> brackets) {
        TaxBreakdown breakdown = new TaxBreakdown();
        double remainingIncome = income;
        double previousThreshold = 0.0;

        for (int i = 0; i < brackets.size() && remainingIncome > 0; i++) {
            RulePack.TaxBracket bracket = brackets.get(i);
            Double upTo = bracket.getUpTo();
            double taxableInBand = upTo == null
                    ? remainingIncome
                    : Math.min(remainingIncome, upTo - previousThreshold);

            if (taxableInBand > 0) {
                breakdown.addBand(i, taxableInBand, bracket.getRate(), taxableInBand * bracket.getRate());
                remainingIncome -= taxableInBand;
            }

            if (upTo == null) {
                break;
            }
            previousThreshold = upTo;
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
