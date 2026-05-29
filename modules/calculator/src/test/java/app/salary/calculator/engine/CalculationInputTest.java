package app.salary.calculator.engine;

import app.salary.common.constants.Country;
import app.salary.common.constants.FilingStatus;
import app.salary.common.constants.PayCadence;
import app.salary.common.dto.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CalculationInputTest {

    @Test
    void from_withAllFields_shouldMapCorrectly() {
        CalculateRequest request = new CalculateRequest();
        request.setCountry(Country.US);
        request.setTaxYear(2025);
        request.setAnnualSalary(100000.0);
        request.setCadence(PayCadence.MONTHLY);

        Pretax pretax = new Pretax();
        pretax.setPercent(0.05);
        pretax.setHsa(3000.0);
        request.setPretax(pretax);

        Posttax posttax = new Posttax();
        posttax.setFixed(100.0);
        request.setPosttax(posttax);

        CountryOptions countryOptions = new CountryOptions();
        CountryOptionsUS usOptions = new CountryOptionsUS();
        usOptions.setState("CA");
        usOptions.setFilingStatus(FilingStatus.SINGLE);
        usOptions.setAllowances(1);
        countryOptions.setUs(usOptions);
        request.setCountryOptions(countryOptions);

        CalculationInput input = CalculationInput.from(request);

        assertNotNull(input);
        assertEquals(Country.US, input.getCountry());
        assertEquals(2025, input.getTaxYear());
        assertEquals(100000.0, input.getAnnualGross());
        assertEquals(PayCadence.MONTHLY, input.getPayCadence());
        assertEquals(pretax, input.getPretax());
        assertEquals(posttax, input.getPosttax());
        assertEquals(usOptions, input.getUsOptions());
        assertNull(input.getUkOptions());
    }

    @Test
    void from_withNullPretax_shouldCreateDefaultPretax() {
        CalculateRequest request = new CalculateRequest();
        request.setCountry(Country.UK);
        request.setTaxYear(2025);
        request.setAnnualSalary(50000.0);
        request.setPretax(null);

        CalculationInput input = CalculationInput.from(request);

        assertNotNull(input.getPretax());
        assertEquals(0.0, input.getPretax().getPercent());
        assertEquals(0.0, input.getPretax().getFixed());
    }

    @Test
    void from_withNullPosttax_shouldCreateDefaultPosttax() {
        CalculateRequest request = new CalculateRequest();
        request.setCountry(Country.UK);
        request.setTaxYear(2025);
        request.setAnnualSalary(50000.0);
        request.setPosttax(null);

        CalculationInput input = CalculationInput.from(request);

        assertNotNull(input.getPosttax());
        assertEquals(0.0, input.getPosttax().getFixed());
        assertNull(input.getPosttax().getStudentLoanPlan());
    }

    @Test
    void from_withNullCountryOptions_shouldHaveNullUsAndUkOptions() {
        CalculateRequest request = new CalculateRequest();
        request.setCountry(Country.UK);
        request.setTaxYear(2025);
        request.setAnnualSalary(50000.0);
        request.setCountryOptions(null);

        CalculationInput input = CalculationInput.from(request);

        assertNull(input.getUsOptions());
        assertNull(input.getUkOptions());
    }

    @Test
    void from_withUkCountryOptions_shouldMapUkOptions() {
        CalculateRequest request = new CalculateRequest();
        request.setCountry(Country.UK);
        request.setTaxYear(2025);
        request.setAnnualSalary(50000.0);

        CountryOptions countryOptions = new CountryOptions();
        CountryOptionsUK ukOptions = new CountryOptionsUK();
        ukOptions.setTaxCode("1257L");
        ukOptions.setScottishResident(true);
        ukOptions.setNiCategory("A");
        countryOptions.setUk(ukOptions);
        request.setCountryOptions(countryOptions);

        CalculationInput input = CalculationInput.from(request);

        assertNotNull(input.getUkOptions());
        assertEquals("1257L", input.getUkOptions().getTaxCode());
        assertEquals(true, input.getUkOptions().getScottishResident());
        assertEquals("A", input.getUkOptions().getNiCategory());
        assertNull(input.getUsOptions());
    }

    @Test
    void from_withMinimalRequest_shouldUseDefaults() {
        CalculateRequest request = new CalculateRequest();
        request.setCountry(Country.UK);
        request.setTaxYear(2025);
        request.setAnnualSalary(40000.0);
        request.setCadence(PayCadence.ANNUAL);

        CalculationInput input = CalculationInput.from(request);

        assertNotNull(input);
        assertEquals(Country.UK, input.getCountry());
        assertEquals(2025, input.getTaxYear());
        assertEquals(40000.0, input.getAnnualGross());
        assertEquals(PayCadence.ANNUAL, input.getPayCadence());
        assertNotNull(input.getPretax());
        assertNotNull(input.getPosttax());
    }

    @Test
    void from_withUsMarriedFilingStatus_shouldMapCorrectly() {
        CalculateRequest request = new CalculateRequest();
        request.setCountry(Country.US);
        request.setTaxYear(2025);
        request.setAnnualSalary(150000.0);

        CountryOptions countryOptions = new CountryOptions();
        CountryOptionsUS usOptions = new CountryOptionsUS();
        usOptions.setState("NY");
        usOptions.setFilingStatus(FilingStatus.MARRIED);
        usOptions.setAllowances(2);
        countryOptions.setUs(usOptions);
        request.setCountryOptions(countryOptions);

        CalculationInput input = CalculationInput.from(request);

        assertNotNull(input.getUsOptions());
        assertEquals("NY", input.getUsOptions().getState());
        assertEquals(FilingStatus.MARRIED, input.getUsOptions().getFilingStatus());
        assertEquals(2, input.getUsOptions().getAllowances());
    }

    // ── Earnings normalization (Phase 2) ────────────────────────────────────────

    @Test
    void from_withEarningsSalaryPerYear_shouldNormalizeToAnnual() {
        CalculateRequest request = new CalculateRequest();
        request.setCountry(Country.US);
        request.setTaxYear(2025);
        request.setCadence(PayCadence.BIWEEKLY);

        Salary salary = new Salary();
        salary.setAmount(200000.0);
        salary.setBasis(Salary.Basis.PER_YEAR);
        Earnings earnings = new Earnings();
        earnings.setSalary(salary);
        request.setEarnings(earnings);

        CalculationInput input = CalculationInput.from(request);

        assertEquals(200000.0, input.getSalaryAnnual());
        assertEquals(200000.0, input.getAnnualGross());
        assertEquals(0.0, input.getBonusAnnual());
    }

    @Test
    void from_withEarningsSalaryPerPeriod_shouldMultiplyByPeriods() {
        CalculateRequest request = new CalculateRequest();
        request.setCountry(Country.US);
        request.setTaxYear(2025);
        request.setCadence(PayCadence.BIWEEKLY);

        Salary salary = new Salary();
        salary.setAmount(5000.0);
        salary.setBasis(Salary.Basis.PER_PERIOD);
        Earnings earnings = new Earnings();
        earnings.setSalary(salary);
        request.setEarnings(earnings);

        CalculationInput input = CalculationInput.from(request);

        // 5000 per period × 26 biweekly periods
        assertEquals(130000.0, input.getSalaryAnnual());
        assertEquals(130000.0, input.getAnnualGross());
    }

    @Test
    void from_withHourlyEarnings_shouldComputeAnnualRegularAndOvertime() {
        CalculateRequest request = new CalculateRequest();
        request.setCountry(Country.US);
        request.setTaxYear(2025);
        request.setCadence(PayCadence.WEEKLY);

        Hourly hourly = new Hourly();
        hourly.setRate(20.0);
        hourly.setRegularHours(40.0);
        hourly.setOvertimeHours(5.0);
        hourly.setOvertimeMultiplier(1.5);
        Earnings earnings = new Earnings();
        earnings.setHourly(hourly);
        request.setEarnings(earnings);

        CalculationInput input = CalculationInput.from(request);

        // Regular: 20 × 40 × 52 = 41600
        // Overtime: 20 × 1.5 × 5 × 52 = 7800
        assertEquals(41600.0, input.getSalaryAnnual(), 0.01);
        assertEquals(7800.0, input.getOvertimeAnnual(), 0.01);
        assertEquals(49400.0, input.getAnnualGross(), 0.01);
    }

    @Test
    void from_withBonusAndCommissionInEarnings_shouldAddToSupplemental() {
        CalculateRequest request = new CalculateRequest();
        request.setCountry(Country.US);
        request.setTaxYear(2025);
        request.setCadence(PayCadence.ANNUAL);

        Salary salary = new Salary();
        salary.setAmount(100000.0);
        salary.setBasis(Salary.Basis.PER_YEAR);
        Earnings earnings = new Earnings();
        earnings.setSalary(salary);
        earnings.setBonus(10000.0);
        earnings.setCommission(5000.0);
        request.setEarnings(earnings);

        CalculationInput input = CalculationInput.from(request);

        assertEquals(100000.0, input.getSalaryAnnual());
        assertEquals(10000.0, input.getBonusAnnual());
        assertEquals(5000.0, input.getCommissionAnnual());
        assertEquals(15000.0, input.getSupplementalAnnual());
        assertEquals(115000.0, input.getAnnualGross());
    }

    @Test
    void from_withoutEarningsOrAnnualSalary_shouldThrow() {
        CalculateRequest request = new CalculateRequest();
        request.setCountry(Country.US);
        request.setTaxYear(2025);
        request.setCadence(PayCadence.ANNUAL);
        // neither annualSalary nor earnings set

        assertThrows(IllegalArgumentException.class, () -> CalculationInput.from(request));
    }

    @Test
    void getRegularWagesAnnual_withExplicitBreakdown_shouldSumComponents() {
        CalculationInput input = new CalculationInput();
        input.setSalaryAnnual(80000.0);
        input.setOvertimeAnnual(5000.0);
        input.setDoubleTimeAnnual(1000.0);
        input.setBonusAnnual(10000.0);

        // explicit breakdown wins; bonus is excluded from regular wages
        assertEquals(86000.0, input.getRegularWagesAnnual(), 0.01);
    }

    @Test
    void getRegularWagesAnnual_withoutBreakdown_shouldFallBackToGrossMinusSupplemental() {
        // Back-compat path: legacy callers that set annualGross + bonusAnnual directly
        CalculationInput input = new CalculationInput();
        input.setAnnualGross(110000.0);
        input.setBonusAnnual(10000.0);
        input.setCommissionAnnual(5000.0);

        assertEquals(95000.0, input.getRegularWagesAnnual(), 0.01);
    }

    @Test
    void getSupplementalAnnual_handlesNullCommission() {
        CalculationInput input = new CalculationInput();
        input.setBonusAnnual(10000.0);
        input.setCommissionAnnual(null);

        assertEquals(10000.0, input.getSupplementalAnnual(), 0.01);
    }

    @Test
    void gettersAndSetters_shouldWorkCorrectly() {
        CalculationInput input = new CalculationInput();

        input.setCountry(Country.US);
        assertEquals(Country.US, input.getCountry());

        input.setTaxYear(2025);
        assertEquals(2025, input.getTaxYear());

        input.setAnnualGross(75000.0);
        assertEquals(75000.0, input.getAnnualGross());

        input.setPayCadence(PayCadence.WEEKLY);
        assertEquals(PayCadence.WEEKLY, input.getPayCadence());

        Pretax pretax = new Pretax();
        input.setPretax(pretax);
        assertEquals(pretax, input.getPretax());

        Posttax posttax = new Posttax();
        input.setPosttax(posttax);
        assertEquals(posttax, input.getPosttax());

        CountryOptionsUS usOptions = new CountryOptionsUS();
        input.setUsOptions(usOptions);
        assertEquals(usOptions, input.getUsOptions());

        CountryOptionsUK ukOptions = new CountryOptionsUK();
        input.setUkOptions(ukOptions);
        assertEquals(ukOptions, input.getUkOptions());
    }
}
