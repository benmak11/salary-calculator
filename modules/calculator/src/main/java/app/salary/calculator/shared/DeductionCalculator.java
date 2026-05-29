package app.salary.calculator.shared;

import app.salary.common.dto.NamedDeduction;
import app.salary.common.dto.Posttax;
import app.salary.common.dto.Pretax;

public class DeductionCalculator {

    /**
     * Returns the combined total of ALL pre-tax deductions (including benefits and custom items).
     * Used for net-pay arithmetic in calculators.
     */
    public double calculatePretaxDeductions(Pretax pretax, double grossIncome) {
        if (pretax == null) return 0.0;

        double deductions = 0.0;
        if (pretax.getPercent() != null && pretax.getPercent() > 0) {
            deductions += grossIncome * pretax.getPercent();
        }
        if (pretax.getFixed() != null && pretax.getFixed() > 0) {
            deductions += pretax.getFixed();
        }
        if (pretax.getHsa() != null && pretax.getHsa() > 0) {
            deductions += pretax.getHsa();
        }
        if (pretax.getPensionPercent() != null && pretax.getPensionPercent() > 0) {
            deductions += grossIncome * pretax.getPensionPercent();
        }
        // Individual benefit premiums
        if (pretax.getMedical() != null && pretax.getMedical() > 0) {
            deductions += pretax.getMedical();
        }
        if (pretax.getDental() != null && pretax.getDental() > 0) {
            deductions += pretax.getDental();
        }
        if (pretax.getVision() != null && pretax.getVision() > 0) {
            deductions += pretax.getVision();
        }
        if (pretax.getHealthcareFsa() != null && pretax.getHealthcareFsa() > 0) {
            deductions += pretax.getHealthcareFsa();
        }
        if (pretax.getDependentCareFsa() != null && pretax.getDependentCareFsa() > 0) {
            deductions += pretax.getDependentCareFsa();
        }
        // Named custom deductions
        if (pretax.getCustomDeductions() != null) {
            for (NamedDeduction nd : pretax.getCustomDeductions()) {
                if (nd.getAmount() != null && nd.getAmount() > 0) {
                    deductions += nd.getAmount();
                }
            }
        }
        return deductions;
    }

    public double calculatePosttaxDeductions(Posttax posttax) {
        if (posttax == null) return 0.0;
        return posttax.getFixed() != null ? posttax.getFixed() : 0.0;
    }

    /**
     * Roth 401(k) is post-tax federally but applies only to regular wages (excludes bonus/commission).
     * Returns the annual Roth contribution; the caller emits a RETIREMENT-tagged line item and
     * subtracts from net.
     */
    public double calculateRoth401k(Posttax posttax, double regularWagesAnnual) {
        if (posttax == null) return 0.0;
        Double pct = posttax.getRoth401kPercent();
        if (pct == null || pct <= 0) return 0.0;
        return regularWagesAnnual * pct;
    }

    public double calculatePensionContribution(Pretax pretax, double grossIncome) {
        if (pretax == null) return 0.0;
        if (pretax.getPensionPercent() != null && pretax.getPensionPercent() > 0) {
            return grossIncome * pretax.getPensionPercent();
        }
        return 0.0;
    }

    /**
     * Returns only the benefit-unrelated pre-tax total (percent + fixed + HSA) for the
     * "Pre-tax Deductions" catch-all line item. Pension, benefits, and custom deductions
     * are emitted as their own named line items.
     */
    public double calculateGenericPretaxDeductions(Pretax pretax, double grossIncome) {
        if (pretax == null) return 0.0;
        double deductions = 0.0;
        if (pretax.getPercent() != null && pretax.getPercent() > 0) {
            deductions += grossIncome * pretax.getPercent();
        }
        if (pretax.getFixed() != null && pretax.getFixed() > 0) {
            deductions += pretax.getFixed();
        }
        if (pretax.getHsa() != null && pretax.getHsa() > 0) {
            deductions += pretax.getHsa();
        }
        return deductions;
    }
}
