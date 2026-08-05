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
