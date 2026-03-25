package app.salary.calculator.shared;

import app.salary.common.constants.IncomeType;
import app.salary.common.dto.Income;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IncomeCalculatorTest {

    private IncomeCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new IncomeCalculator();
    }

    @Test
    void calculateAnnualGross_withAnnualIncome_shouldReturnAmountDirectly() {
        Income income = new Income();
        income.setType(IncomeType.ANNUAL);
        income.setAmount(75000.0);

        double result = calculator.calculateAnnualGross(income, 40.0);

        assertEquals(75000.0, result, 0.01);
    }

    @Test
    void calculateAnnualGross_withHourlyIncome_andSpecifiedHours_shouldCalculateCorrectly() {
        Income income = new Income();
        income.setType(IncomeType.HOURLY);
        income.setAmount(25.0);
        income.setHoursPerWeek(40.0);

        // 25 * 40 * 52 = 52000
        double result = calculator.calculateAnnualGross(income, 35.0);

        assertEquals(52000.0, result, 0.01);
    }

    @Test
    void calculateAnnualGross_withHourlyIncome_andDefaultHours_shouldUseDefaultHours() {
        Income income = new Income();
        income.setType(IncomeType.HOURLY);
        income.setAmount(30.0);
        income.setHoursPerWeek(null); // Will use default

        // 30 * 40 (default) * 52 = 62400
        double result = calculator.calculateAnnualGross(income, 40.0);

        assertEquals(62400.0, result, 0.01);
    }

    @Test
    void calculateAnnualGross_withHourlyIncome_andPartTimeHours_shouldCalculateCorrectly() {
        Income income = new Income();
        income.setType(IncomeType.HOURLY);
        income.setAmount(20.0);
        income.setHoursPerWeek(20.0);

        // 20 * 20 * 52 = 20800
        double result = calculator.calculateAnnualGross(income, 40.0);

        assertEquals(20800.0, result, 0.01);
    }

    @Test
    void calculateAnnualGross_withHighHourlyRate_shouldCalculateCorrectly() {
        Income income = new Income();
        income.setType(IncomeType.HOURLY);
        income.setAmount(150.0);
        income.setHoursPerWeek(50.0);

        // 150 * 50 * 52 = 390000
        double result = calculator.calculateAnnualGross(income, 40.0);

        assertEquals(390000.0, result, 0.01);
    }

    @Test
    void calculateAnnualGross_withZeroHourlyRate_shouldReturnZero() {
        Income income = new Income();
        income.setType(IncomeType.HOURLY);
        income.setAmount(0.0);
        income.setHoursPerWeek(40.0);

        double result = calculator.calculateAnnualGross(income, 40.0);

        assertEquals(0.0, result, 0.01);
    }

    @Test
    void calculateAnnualGross_withZeroAnnualSalary_shouldReturnZero() {
        Income income = new Income();
        income.setType(IncomeType.ANNUAL);
        income.setAmount(0.0);

        double result = calculator.calculateAnnualGross(income, 40.0);

        assertEquals(0.0, result, 0.01);
    }
}
