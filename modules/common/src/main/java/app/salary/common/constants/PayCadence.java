package app.salary.common.constants;

public enum PayCadence {
    ANNUAL(1),
    MONTHLY(12),
    BIWEEKLY(26),
    WEEKLY(52);

    private final int periodsPerYear;

    PayCadence(int periodsPerYear) {
        this.periodsPerYear = periodsPerYear;
    }

    public int getPeriodsPerYear() {
        return periodsPerYear;
    }
}
