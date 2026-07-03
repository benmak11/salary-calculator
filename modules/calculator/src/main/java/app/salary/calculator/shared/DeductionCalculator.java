package app.salary.calculator.shared;

import app.salary.common.dto.NamedDeduction;
import app.salary.common.dto.Posttax;
import app.salary.common.dto.Pretax;

import java.util.List;

public class DeductionCalculator {

    /**
     * Returns the combined total of ALL pre-tax deductions (including benefits and custom items).
     * Used for net-pay arithmetic in calculators. Equals the generic catch-all total plus every
     * named line item (pension, benefit premiums, FSAs, custom deductions).
     */
    public double calculatePretaxDeductions(Pretax pretax, double grossIncome) {
        if (pretax == null) return 0.0;
        return calculateGenericPretaxDeductions(pretax, grossIncome)
                + calculatePensionContribution(pretax, grossIncome)
                + positiveOrZero(pretax.getMedical())
                + positiveOrZero(pretax.getDental())
                + positiveOrZero(pretax.getVision())
                + positiveOrZero(pretax.getHealthcareFsa())
                + positiveOrZero(pretax.getDependentCareFsa())
                + customDeductionsTotal(pretax.getCustomDeductions());
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
        return regularWagesAnnual * positiveOrZero(posttax.getRoth401kPercent());
    }

    public double calculatePensionContribution(Pretax pretax, double grossIncome) {
        if (pretax == null) return 0.0;
        return grossIncome * positiveOrZero(pretax.getPensionPercent());
    }

    /**
     * Returns only the benefit-unrelated pre-tax total (percent + fixed + HSA) for the
     * "Pre-tax Deductions" catch-all line item. Pension, benefits, and custom deductions
     * are emitted as their own named line items.
     */
    public double calculateGenericPretaxDeductions(Pretax pretax, double grossIncome) {
        if (pretax == null) return 0.0;
        return grossIncome * positiveOrZero(pretax.getPercent())
                + positiveOrZero(pretax.getFixed())
                + positiveOrZero(pretax.getHsa());
    }

    private static double customDeductionsTotal(List<NamedDeduction> customDeductions) {
        if (customDeductions == null) return 0.0;
        double total = 0.0;
        for (NamedDeduction nd : customDeductions) {
            total += positiveOrZero(nd.getAmount());
        }
        return total;
    }

    /** Treats null and non-positive values as "not provided". */
    private static double positiveOrZero(Double value) {
        return value != null && value > 0 ? value : 0.0;
    }
}
