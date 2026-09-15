package app.salary.api.store;

import java.util.Optional;

/**
 * Maps verified provider identities ({@code apple:<sub>}, {@code google:<sub>}) onto a
 * stable, provider-independent {@code accountId}.
 *
 * <p><b>This ships dark.</b> Nothing reads an accountId yet — sessions are still minted
 * with the raw provider sub as the JWT subject, and every user-scoped collection is still
 * keyed on that sub. The directory exists so the mapping is already populated, and already
 * correct, by the time the migration re-keys those collections.
 *
 * <p>The reason it lands before anything else in the backend track: a collection created
 * against a provider sub has to be migrated twice. Writing the mapping first means every
 * later collection can be keyed on accountId from its first line of code.
 *
 * <p>One human who signs in with both Apple and Google still gets two identities today,
 * because nothing merges them. Recording both against the schema is what makes merging
 * possible later; it is not what performs it.
 */
public interface AccountDirectory {
    String PROVIDER_APPLE = "apple";
    String PROVIDER_GOOGLE = "google";

    /**
     * Returns the accountId linked to this provider identity, minting the account and the
     * identity record on first sight. Idempotent: a repeat sign-in returns the same id and
     * only refreshes {@code lastSeenAt}.
     */
    String resolveOrCreate(String provider, String providerSub, String displayName);

    /** The accountId already linked to this identity, or empty when the identity is unknown. */
    Optional<String> findAccountId(String provider, String providerSub);

    /**
     * The accountId for a provider sub without knowing which provider issued it.
     *
     * <p>Exists because {@code AuthMiddleware} only carries the sub — the session JWT does
     * not record the provider. Anything that wants to key new data on accountId today has
     * to go through here.
     *
     * <p>Returns empty for anyone who last signed in before the identity schema shipped;
     * they have no identity record until their next sign-in. Callers must treat a missing
     * accountId as normal rather than as an error.
     */
    Optional<String> findAccountIdBySub(String providerSub);

    /**
     * Whether this account was granted permanent AI-budget access at migration time.
     *
     * <p>Always false today: the flag is written exactly once, by the B1 migration, for
     * accounts that already had a saved budget. It is read here so the subscription gate is
     * already correct on the day that backfill runs, rather than needing a second change
     * then. It grants one feature, not Pro.
     */
    boolean hasLegacyProBudget(String accountId);

    /**
     * Repoints one identity at another account, returning the accountId it used to point at.
     *
     * <p>This is what a redeemed link code performs. Only the redeeming identity moves; the
     * account it came from is left in place rather than deleted, because its data is still
     * keyed on the provider sub until the B1 migration runs, and deleting it here would
     * strand that data with nothing pointing at it.
     *
     * <p>Empty when the sub has no identity record, or when it already points at the target.
     */
    Optional<String> relinkIdentity(String providerSub, String targetAccountId);

    /**
     * Creates an account for a sub whose provider is unknown, without an identity record.
     * Used only by the B-1b backfill.
     *
     * <p><b>Why this exists.</b> {@code users/{sub}} records the sub but not which provider
     * issued it, while an identity is keyed {@code {provider}:{sub}} — so the backfill cannot
     * create identities for anyone who has not signed in since the identity schema landed.
     * Guessing the provider from the shape of the sub was the alternative, and a wrong guess
     * silently orphans that person's migrated data behind a second account.
     *
     * <p>Instead the account is created with the sub recorded on it and no identity at all.
     * The provider is resolved for certain at the next sign-in, when
     * {@link #resolveOrCreate} adopts this account rather than minting a new one.
     *
     * <p>Idempotent: calling it twice for the same sub returns the same account.
     */
    String createLegacyAccount(String providerSub, String displayName);

    /**
     * Deletes the account reachable from this provider sub along with <em>every</em> identity
     * pointing at it, and returns how many identity records were removed.
     *
     * <p>Deliberately keyed on the sub rather than the accountId: account deletion is driven
     * by {@code AuthMiddleware}'s userId, which is still a provider sub. Resolving to the
     * account first and then sweeping by accountId is what stops a second linked identity
     * from being left behind pointing at a document that no longer exists.
     */
    int deleteByProviderSub(String providerSub);
}
