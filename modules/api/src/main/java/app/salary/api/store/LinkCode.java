package app.salary.api.store;

import java.time.Instant;

/**
 * A short-lived code that lets a second sign-in provider be attached to an existing account.
 *
 * <p>Six digits over ten minutes is a 10^6 space, which is only safe because redemption is
 * attempt-limited per code. Without that counter the code length would have to grow: a
 * per-caller rate limit does not protect it, since the budget it allows is thousands of
 * guesses inside one code's lifetime.
 *
 * @param code       the six-digit code, and the document id
 * @param accountId  the account that generated it, and the account a redeemer joins
 * @param expiresAt  ten minutes after issue
 * @param attempts   failed redemption attempts recorded against this code
 * @param redeemed   set once, on success; a code is single-use
 */
public record LinkCode(
        String code,
        String accountId,
        Instant createdAt,
        Instant expiresAt,
        int attempts,
        boolean redeemed) {

    /** Redemption attempts allowed before the code is burned. */
    public static final int MAX_ATTEMPTS = 5;

    public boolean isExpiredAt(Instant now) {
        return expiresAt == null || !expiresAt.isAfter(now);
    }

    /** Usable means live, unredeemed, and not yet burned by failed guesses. */
    public boolean isUsableAt(Instant now) {
        return !redeemed && !isExpiredAt(now) && attempts < MAX_ATTEMPTS;
    }
}
