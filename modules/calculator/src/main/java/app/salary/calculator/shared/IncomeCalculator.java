package app.salary.calculator.shared;

import app.salary.common.constants.IncomeType;
import app.salary.common.dto.Income;
import org.springframework.stereotype.Component;

@Component
public class IncomeCalculator {
    public double calculateAnnualGross(Income income, double defaultHoursPerWeek) {
        if (income.getType() == IncomeType.ANNUAL) {
            return income.getAmount();
        } else {
            double hoursPerWeek = income.getHoursPerWeek() != null
                    ? income.getHoursPerWeek()
                    : defaultHoursPerWeek;
            return income.getAmount() * hoursPerWeek * 52;
        }
    }
}
