package app.salary.api.store;

import java.util.List;

/**
 * Stores every store's entitlement record for an account, keyed on accountId.
 *
 * <p><b>Ships dark.</b> Nothing writes an entitlement yet — the writers arrive with
 * subscription verification and Real-time Developer Notifications. The schema lands first so
 * those writers, and the resolution rule, are already keyed on accountId rather than on a
 * provider sub that a later migration would have to re-key.
 *
 * <p>The one invariant worth stating in the interface: {@link #upsert} replaces a single
 * store's record and must never disturb another store's. See {@link Entitlement} for why
 * collapsing them is the cross-platform downgrade bug.
 */
public interface EntitlementStore {

    /** Writes one store's record, leaving every other store's record untouched. */
    void upsert(String accountId, Entitlement entitlement);

    /** Every store's record for this account, in no particular order. Empty when unknown. */
    List<Entitlement> findAll(String accountId);

    /** Removes every record for the account. Used by account deletion. */
    void deleteAll(String accountId);
}
