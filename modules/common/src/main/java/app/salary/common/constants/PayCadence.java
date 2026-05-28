package app.salary.common.constants;

public enum PayCadence {
    ANNUAL(1),
    SEMIANNUAL(2),
    QUARTERLY(4),
    MONTHLY(12),
    SEMIMONTHLY(24),
    BIWEEKLY(26),
    WEEKLY(52),
    DAILY(260);

    private final int periodsPerYear;

    PayCadence(int periodsPerYear) {
        this.periodsPerYear = periodsPerYear;
    }

    public int getPeriodsPerYear() {
        return periodsPerYear;
    }
}
