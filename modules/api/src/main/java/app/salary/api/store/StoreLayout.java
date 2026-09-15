package app.salary.api.store;

/**
 * Which key a store's documents hang from.
 *
 * <p>The B-1b migration re-keys {@code calculations}, {@code grants} and {@code budget} from
 * the provider sub onto accountId. Both layouts have to be live at once during the
 * migration, so the Firestore stores take this rather than having a second copy of each
 * class written against the new paths. The serialisation logic is where a divergence would
 * actually hurt, and there is only one copy of it.
 *
 * <p>Collections created after the identity schema are account-keyed already and do not
 * need this.
 */
public enum StoreLayout {

    /** {@code users/{sub}/calculations/{id}} — the original layout, still authoritative. */
    SUB_KEYED,

    /** {@code calculations/{accountId}/entries/{id}} — mirrors the shape {@code checkIns} uses. */
    ACCOUNT_KEYED
}
