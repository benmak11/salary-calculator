package app.salary.calculator.shared;

import app.salary.common.constants.StudentLoanPlan;
import app.salary.rules.RulePack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    @Test
    void calculateRepayment_withMissingPlanRules_shouldReturnZero() {
        RulePack rules = createRulesWithStudentLoans();
        // POSTGRAD not in our test data

        double result = calculator.calculateRepayment(StudentLoanPlan.POSTGRAD, 50000.0, rules);

        assertEquals(0.0, result, 0.01);
    }

    @Test
    void calculateRepayment_withIncomeBelowThreshold_shouldReturnZero() {
        RulePack rules = createRulesWithStudentLoans();

        // Plan 2 threshold is 27295, income is 25000
        double result = calculator.calculateRepayment(StudentLoanPlan.PLAN2, 25000.0, rules);

        assertEquals(0.0, result, 0.01);
    }

    @Test
    void calculateRepayment_withIncomeAtThreshold_shouldReturnZero() {
        RulePack rules = createRulesWithStudentLoans();

        // Plan 2 threshold is 27295
        double result = calculator.calculateRepayment(StudentLoanPlan.PLAN2, 27295.0, rules);

        assertEquals(0.0, result, 0.01);
    }

    @Test
    void calculateRepayment_plan2_withIncomeAboveThreshold_shouldCalculateCorrectly() {
        RulePack rules = createRulesWithStudentLoans();

        // Income: 50000, Threshold: 27295, Rate: 9%
        // Repayment: (50000 - 27295) * 0.09 = 2043.45
        double result = calculator.calculateRepayment(StudentLoanPlan.PLAN2, 50000.0, rules);

        assertEquals(2043.45, result, 0.01);
    }

    @Test
    void calculateRepayment_plan1_withIncomeAboveThreshold_shouldCalculateCorrectly() {
        RulePack rules = createRulesWithStudentLoans();

        // Income: 50000, Threshold: 22015, Rate: 9%
        // Repayment: (50000 - 22015) * 0.09 = 2518.65
        double result = calculator.calculateRepayment(StudentLoanPlan.PLAN1, 50000.0, rules);

        assertEquals(2518.65, result, 0.01);
    }

    @Test
    void calculateRepayment_withHighIncome_shouldCalculateCorrectly() {
        RulePack rules = createRulesWithStudentLoans();

        // Income: 100000, Threshold: 27295, Rate: 9%
        // Repayment: (100000 - 27295) * 0.09 = 6543.45
        double result = calculator.calculateRepayment(StudentLoanPlan.PLAN2, 100000.0, rules);

        assertEquals(6543.45, result, 0.01);
    }

    @Test
    void calculateRepayment_withIncomeJustAboveThreshold_shouldCalculateCorrectly() {
        RulePack rules = createRulesWithStudentLoans();

        // Income: 30000, Threshold: 27295, Rate: 9%
        // Repayment: (30000 - 27295) * 0.09 = 243.45
        double result = calculator.calculateRepayment(StudentLoanPlan.PLAN2, 30000.0, rules);

        assertEquals(243.45, result, 0.01);
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
