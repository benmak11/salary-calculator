package app.salary.api.store;

import java.util.Optional;

/**
 * Issues and redeems account link codes.
 *
 * <p><b>Ships dark.</b> The endpoints exist and work, but linking two identities has no
 * visible effect until the B1 migration re-keys user data onto accountId: today
 * {@code calculations}, {@code grants} and {@code budget} still live under the provider sub,
 * so a linked pair still reads two separate histories. What linking does establish now is
 * the identity graph the migration will consolidate against, and the entitlement merge.
 */
public interface LinkCodeStore {

    /**
     * Issues a code for this account, replacing any outstanding one.
     *
     * <p>Replacing rather than accumulating keeps at most one live code per account, so a
     * user who taps generate repeatedly does not widen the guessable set.
     */
    LinkCode issue(String accountId);

    Optional<LinkCode> find(String code);

    /**
     * Records a failed guess and returns the code's state afterwards.
     *
     * <p>The counter lives on the code, not on the caller: the caller is whoever is guessing,
     * and they are free to change IP. Empty when the code does not exist, which is the
     * common case for a wrong guess.
     */
    Optional<LinkCode> recordFailedAttempt(String code);

    /** Marks a code used. Returns false if it was already redeemed, which makes this a no-op. */
    boolean markRedeemed(String code);

    /** Removes any codes belonging to the account. Used by account deletion. */
    void deleteByAccountId(String accountId);
}
