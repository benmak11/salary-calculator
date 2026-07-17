package app.salary.calculator.engine;

import app.salary.common.constants.Country;
import app.salary.common.constants.PayCadence;
import app.salary.common.dto.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class CalculationInput {
    private Country country;
    private Integer taxYear;
    private Double annualGross;
    private Double salaryAnnual = 0.0;
    private Double overtimeAnnual = 0.0;
    private Double doubleTimeAnnual = 0.0;
    private Double bonusAnnual = 0.0;
    private Double commissionAnnual = 0.0;
    private Double rsuVestingAnnual = 0.0;
    private Integer bonusDeferredToYear;
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
            applyBonusInclusionRule(input, earnings);
            input.commissionAnnual = earnings.getCommission() != null ? earnings.getCommission() : 0.0;
            input.rsuVestingAnnual = earnings.getRsuVesting() != null ? earnings.getRsuVesting() : 0.0;
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
                + input.commissionAnnual
                + input.rsuVestingAnnual;

        return input;
    }

    /**
     * A bonus counts toward the requested tax year when its payout year matches (one-time)
     * or has already started (recurring). Otherwise it is excluded from the calculation
     * entirely and {@code bonusDeferredToYear} records where it lands — the client shows it
     * in the yearly earnings outlook instead of this year's paycheck.
     */
    private static void applyBonusInclusionRule(CalculationInput input, Earnings earnings) {
        double bonus = earnings.getBonus() != null ? earnings.getBonus() : 0.0;
        if (bonus <= 0) {
            input.bonusAnnual = 0.0;
            return;
        }
        int payoutYear = parsePayoutYear(earnings.getBonusDate(), input.taxYear);
        boolean recurring = Boolean.TRUE.equals(earnings.getBonusRecurring());
        boolean included = recurring ? payoutYear <= input.taxYear : payoutYear == input.taxYear;
        if (included) {
            input.bonusAnnual = bonus;
        } else {
            input.bonusAnnual = 0.0;
            input.bonusDeferredToYear = payoutYear;
        }
    }

    /** Lenient ISO yyyy-MM-dd parse; anything unparseable falls back to the tax year. */
    private static int parsePayoutYear(String bonusDate, Integer taxYear) {
        if (bonusDate == null || bonusDate.isBlank()) return taxYear;
        try {
            return java.time.LocalDate.parse(bonusDate.trim()).getYear();
        } catch (java.time.format.DateTimeParseException e) {
            return taxYear;
        }
    }

    public double getSupplementalAnnual() {
        double b = bonusAnnual != null ? bonusAnnual : 0.0;
        double c = commissionAnnual != null ? commissionAnnual : 0.0;
        double r = rsuVestingAnnual != null ? rsuVestingAnnual : 0.0;
        return b + c + r;
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
}
