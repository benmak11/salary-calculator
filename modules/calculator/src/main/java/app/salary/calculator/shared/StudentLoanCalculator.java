package app.salary.calculator.shared;

import app.salary.common.constants.StudentLoanPlan;
import app.salary.rules.RulePack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class StudentLoanCalculator {
    private static final Logger log = LoggerFactory.getLogger(StudentLoanCalculator.class);

    public double calculateRepayment(StudentLoanPlan plan, double taxableIncome, RulePack rules) {
        if (plan == null || rules.getStudentLoan() == null) {
            return 0.0;
        }

        String planKey = plan.name().toLowerCase();
        RulePack.StudentLoanRules loanRules = rules.getStudentLoan().get(planKey);

        if (loanRules == null) {
            log.warn("No student loan rules found for plan: {}", plan);
            return 0.0;
        }

        if (taxableIncome <= loanRules.getThreshold()) {
            return 0.0;
        }

        double excessIncome = taxableIncome - loanRules.getThreshold();
        return excessIncome * loanRules.getRate();
    }
}