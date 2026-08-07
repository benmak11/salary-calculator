package app.salary.api.store;

import java.time.Instant;

/**
 * One store's view of an account's subscription. An account can hold several of these at
 * once, and they are deliberately kept side by side rather than reconciled into a single
 * record.
 *
 * <p>A human who subscribed on iPhone and later signs in on Android has an App Store
 * entitlement and no Play one. Collapsing the pair into "the" entitlement is how the naive
 * implementation revokes Pro from every cross-platform subscriber: whichever store answered
 * last wins, and the other store's paid-up record is gone.
 *
 * @param store       {@link #APP_STORE} or {@link #PLAY}
 * @param productId   the store's product identifier, for support and analytics
 * @param expiresAt   when access lapses; null means "no expiry recorded", which never grants
 * @param revoked     set by a refund or a Voided Purchases notification; wins over expiresAt
 * @param updatedAt   when this record was last written, for debugging RDN ordering
 */
public record Entitlement(
        String store,
        String productId,
        Instant expiresAt,
        boolean revoked,
        Instant updatedAt) {

    public static final String APP_STORE = "app_store";
    public static final String PLAY = "play";

    /**
     * Whether this single record grants access right now.
     *
     * <p>Revocation is checked first and unconditionally: a refunded subscription can still
     * carry a future {@code expiresAt}, and honouring that would hand Pro to everyone who
     * charged back.
     */
    public boolean grantsAccessAt(Instant now) {
        return !revoked && expiresAt != null && expiresAt.isAfter(now);
    }
}
