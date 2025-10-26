package app.salary.calculator.shared;

import app.salary.common.dto.Posttax;
import app.salary.common.dto.Pretax;
import org.springframework.stereotype.Component;

@Component
public class DeductionCalculator {
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
        return deductions;
    }

    public double calculatePosttaxDeductions(Posttax posttax) {
        if (posttax == null) return 0.0;
        return posttax.getFixed() != null ? posttax.getFixed() : 0.0;
    }

    public double calculatePensionContribution(Pretax pretax, double grossIncome) {
        if (pretax == null) return 0.0;
        if (pretax.getPensionPercent() != null && pretax.getPensionPercent() > 0) {
            return grossIncome * pretax.getPensionPercent();
        }
        return 0.0;
    }
}
