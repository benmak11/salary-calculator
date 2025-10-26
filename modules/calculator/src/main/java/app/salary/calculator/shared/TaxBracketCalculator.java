package app.salary.calculator.shared;

import app.salary.rules.RulePack;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class TaxBracketCalculator {
    public double calculateTax(double income, List<RulePack.TaxBracket> brackets) {
        double tax = 0.0;
        double previousThreshold = 0.0;

        for (RulePack.TaxBracket bracket : brackets) {
            if (bracket.getUpTo() == null) {
                tax += (income - previousThreshold) * bracket.getRate();
                break;
            } else if (income > bracket.getUpTo()) {
                tax += (bracket.getUpTo() - previousThreshold) * bracket.getRate();
                previousThreshold = bracket.getUpTo();
            } else {
                tax += (income - previousThreshold) * bracket.getRate();
                break;
            }
        }
        return tax;
    }

    public TaxBreakdown calculateTaxWithBreakdown(double income, List<RulePack.TaxBracket> brackets) {
        TaxBreakdown breakdown = new TaxBreakdown();
        double remainingIncome = income;
        double previousThreshold = 0.0;

        for (int i = 0; i < brackets.size(); i++) {
            RulePack.TaxBracket bracket = brackets.get(i);

            if (bracket.getUpTo() == null) {
                if (remainingIncome > 0) {
                    double taxInBand = remainingIncome * bracket.getRate();
                    breakdown.addBand(i, remainingIncome, bracket.getRate(), taxInBand);
                }
                break;
            } else {
                double bandWidth = bracket.getUpTo() - previousThreshold;
                double taxableInBand = Math.min(remainingIncome, bandWidth);

                if (taxableInBand > 0) {
                    double taxInBand = taxableInBand * bracket.getRate();
                    breakdown.addBand(i, taxableInBand, bracket.getRate(), taxInBand);
                    remainingIncome -= taxableInBand;
                }

                previousThreshold = bracket.getUpTo();
                if (remainingIncome <= 0) break;
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
