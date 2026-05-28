package app.salary.calculator.engine;

import app.salary.common.constants.Country;
import app.salary.common.constants.PayCadence;
import app.salary.common.dto.*;

public class CalculationInput {
    private Country country;
    private Integer taxYear;
    private Double annualGross;
    private Double salaryAnnual = 0.0;
    private Double overtimeAnnual = 0.0;
    private Double doubleTimeAnnual = 0.0;
    private Double bonusAnnual = 0.0;
    private Double commissionAnnual = 0.0;
    private PayCadence payCadence;
    private Pretax pretax;
    private Posttax posttax;
    private CountryOptionsUS usOptions;
    private CountryOptionsUK ukOptions;

    public static CalculationInput from(CalculateRequest request) {
        CalculationInput input = new CalculationInput();
        input.country = request.getCountry();
        input.taxYear = request.getTaxYear();
        input.payCadence = request.getCadence();
        input.pretax = request.getPretax() != null ? request.getPretax() : new Pretax();
        input.posttax = request.getPosttax() != null ? request.getPosttax() : new Posttax();

        if (request.getCountryOptions() != null) {
            input.usOptions = request.getCountryOptions().getUs();
            input.ukOptions = request.getCountryOptions().getUk();
        }

        int periods = request.getCadence().getPeriodsPerYear();
        Earnings earnings = request.getEarnings();

        if (earnings != null) {
            // Structured earnings path
            if (earnings.getSalary() != null && earnings.getSalary().getAmount() != null) {
                double amt = earnings.getSalary().getAmount();
                input.salaryAnnual = (earnings.getSalary().getBasis() == Salary.Basis.PER_PERIOD)
                        ? amt * periods
                        : amt;
            }
            if (earnings.getHourly() != null && earnings.getHourly().getRate() != null) {
                Hourly h = earnings.getHourly();
                double rate = h.getRate();
                double regHrs = h.getRegularHours() != null ? h.getRegularHours() : 0.0;
                double otHrs  = h.getOvertimeHours() != null ? h.getOvertimeHours() : 0.0;
                double otMult = h.getOvertimeMultiplier() != null ? h.getOvertimeMultiplier() : 1.5;
                double dtHrs  = h.getDoubleTimeHours() != null ? h.getDoubleTimeHours() : 0.0;
                double dtMult = h.getDoubleTimeMultiplier() != null ? h.getDoubleTimeMultiplier() : 2.0;
                input.salaryAnnual += rate * regHrs * periods;
                input.overtimeAnnual = rate * otMult * otHrs * periods;
                input.doubleTimeAnnual = rate * dtMult * dtHrs * periods;
            }
            input.bonusAnnual = earnings.getBonus() != null ? earnings.getBonus() : 0.0;
            input.commissionAnnual = earnings.getCommission() != null ? earnings.getCommission() : 0.0;
        } else {
            // Back-compat path: annualSalary + bonus
            if (request.getAnnualSalary() == null) {
                throw new IllegalArgumentException("CalculateRequest must include either `earnings` or `annualSalary`");
            }
            input.salaryAnnual = request.getAnnualSalary();
            input.bonusAnnual = request.getBonus() != null ? request.getBonus() : 0.0;
        }

        input.annualGross = input.salaryAnnual
                + input.overtimeAnnual
                + input.doubleTimeAnnual
                + input.bonusAnnual
                + input.commissionAnnual;

        return input;
    }

    public double getSupplementalAnnual() {
        double b = bonusAnnual != null ? bonusAnnual : 0.0;
        double c = commissionAnnual != null ? commissionAnnual : 0.0;
        return b + c;
    }

    public double getRegularWagesAnnual() {
        double explicit = (salaryAnnual != null ? salaryAnnual : 0.0)
                + (overtimeAnnual != null ? overtimeAnnual : 0.0)
                + (doubleTimeAnnual != null ? doubleTimeAnnual : 0.0);
        if (explicit > 0) return explicit;
        // Back-compat: when callers set annualGross directly (legacy / tests) without the
        // salary/OT/DT breakdown, derive regular wages as gross minus supplemental.
        if (annualGross != null) {
            return Math.max(0, annualGross - getSupplementalAnnual());
        }
        return 0;
    }

    public Country getCountry() { return country; }
    public void setCountry(Country country) { this.country = country; }
    public Integer getTaxYear() { return taxYear; }
    public void setTaxYear(Integer taxYear) { this.taxYear = taxYear; }
    public Double getAnnualGross() { return annualGross; }
    public void setAnnualGross(Double annualGross) { this.annualGross = annualGross; }
    public Double getSalaryAnnual() { return salaryAnnual; }
    public void setSalaryAnnual(Double salaryAnnual) { this.salaryAnnual = salaryAnnual; }
    public Double getOvertimeAnnual() { return overtimeAnnual; }
    public void setOvertimeAnnual(Double overtimeAnnual) { this.overtimeAnnual = overtimeAnnual; }
    public Double getDoubleTimeAnnual() { return doubleTimeAnnual; }
    public void setDoubleTimeAnnual(Double doubleTimeAnnual) { this.doubleTimeAnnual = doubleTimeAnnual; }
    public Double getBonusAnnual() { return bonusAnnual; }
    public void setBonusAnnual(Double bonusAnnual) { this.bonusAnnual = bonusAnnual; }
    public Double getCommissionAnnual() { return commissionAnnual; }
    public void setCommissionAnnual(Double commissionAnnual) { this.commissionAnnual = commissionAnnual; }
    public PayCadence getPayCadence() { return payCadence; }
    public void setPayCadence(PayCadence payCadence) { this.payCadence = payCadence; }
    public Pretax getPretax() { return pretax; }
    public void setPretax(Pretax pretax) { this.pretax = pretax; }
    public Posttax getPosttax() { return posttax; }
    public void setPosttax(Posttax posttax) { this.posttax = posttax; }
    public CountryOptionsUS getUsOptions() { return usOptions; }
    public void setUsOptions(CountryOptionsUS usOptions) { this.usOptions = usOptions; }
    public CountryOptionsUK getUkOptions() { return ukOptions; }
    public void setUkOptions(CountryOptionsUK ukOptions) { this.ukOptions = ukOptions; }
}
