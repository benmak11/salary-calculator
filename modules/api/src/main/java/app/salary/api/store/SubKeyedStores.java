package app.salary.api.store;

/**
 * The stores still keyed on the <b>provider sub</b>, grouped because that shared key is a
 * real property rather than a convenience: these four are exactly what the pending B-1b
 * migration has to re-key onto accountId.
 *
 * <p>Grouping them also keeps {@code AccountController}'s constructor honest. Account
 * deletion legitimately touches a lot of stores, and listing eight of them flat said nothing
 * about which were related; split by key, the parameter list now mirrors the migration
 * boundary.
 *
 * <p>Anything created after the identity schema belongs in {@link AccountKeyedStores}
 * instead, so this set only ever shrinks.
 */
public record SubKeyedStores(
        CalculationStore calculations,
        GrantStore grants,
        BudgetStore budgets,
        UserDirectory users) {
}
