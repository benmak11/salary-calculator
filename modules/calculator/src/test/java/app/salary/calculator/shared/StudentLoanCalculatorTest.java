package app.salary.calculator.shared;

import app.salary.common.constants.StudentLoanPlan;
import app.salary.rules.RulePack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StudentLoanCalculatorTest {

    private StudentLoanCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new StudentLoanCalculator();
    }

    @Test
    void calculateRepayment_withNullPlan_shouldReturnZero() {
        RulePack rules = createRulesWithStudentLoans();

        double result = calculator.calculateRepayment(null, 50000.0, rules);

        assertEquals(0.0, result, 0.01);
    }

    @Test
    void calculateRepayment_withNullStudentLoanRules_shouldReturnZero() {
        RulePack rules = new RulePack();
        rules.setStudentLoan(null);

        double result = calculator.calculateRepayment(StudentLoanPlan.PLAN2, 50000.0, rules);

        assertEquals(0.0, result, 0.01);
    }

    @ParameterizedTest(name = "{0} on income {1} -> repayment {2}")
    @CsvSource({
            // Plan 1 threshold: 22015, Plan 2 threshold: 27295, rate 9% above threshold
            "POSTGRADUATE,  50000.0,     0.0", // plan not in rule pack
            "PLAN2,         25000.0,     0.0", // below threshold
            "PLAN2,         27295.0,     0.0", // exactly at threshold
            "PLAN2,         30000.0,   243.45", // just above: (30000 - 27295) * 0.09
            "PLAN2,         50000.0,  2043.45", // (50000 - 27295) * 0.09
            "PLAN2,        100000.0,  6543.45", // high income: (100000 - 27295) * 0.09
            "PLAN1,         50000.0,  2518.65", // (50000 - 22015) * 0.09
    })
    void calculateRepayment_shouldApplyRateToIncomeAboveThreshold(StudentLoanPlan plan, double income, double expected) {
        RulePack rules = createRulesWithStudentLoans();

        double result = calculator.calculateRepayment(plan, income, rules);

        assertEquals(expected, result, 0.01);
    }

    private RulePack createRulesWithStudentLoans() {
        RulePack rules = new RulePack();
        Map<String, RulePack.StudentLoanRules> studentLoanMap = new HashMap<>();

        // Plan 1 rules
        RulePack.StudentLoanRules plan1 = new RulePack.StudentLoanRules();
        plan1.setThreshold(22015.0);
        plan1.setRate(0.09);
        studentLoanMap.put("plan1", plan1);

        // Plan 2 rules
        RulePack.StudentLoanRules plan2 = new RulePack.StudentLoanRules();
        plan2.setThreshold(27295.0);
        plan2.setRate(0.09);
        studentLoanMap.put("plan2", plan2);

        rules.setStudentLoan(studentLoanMap);
        return rules;
    }
}
