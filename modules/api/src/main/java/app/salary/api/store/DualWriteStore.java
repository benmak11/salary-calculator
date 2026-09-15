package app.salary.api.store;

import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Shared machinery for the B-1b dual-write decorators.
 *
 * <p>Every operation on all three stores has the same shape: work out which layout is
 * authoritative right now, act on it, then mirror the write to the other one. Written out
 * per method that is roughly twenty near-identical if/else blocks, and each one is a place
 * the two layouts could be wired up subtly differently. Resolving the two sides <em>once</em>
 * leaves the decorators to say only what their operation actually is.
 *
 * @param <S> the store interface being decorated
 */
abstract class DualWriteStore<S> {

    private final S subKeyed;
    private final S accountKeyed;
    private final AccountIdResolver resolver;
    private final boolean readAccountKeyed;

    protected DualWriteStore(S subKeyed, S accountKeyed,
                             AccountIdResolver resolver, boolean readAccountKeyed) {
        this.subKeyed = subKeyed;
        this.accountKeyed = accountKeyed;
        this.resolver = resolver;
        this.readAccountKeyed = readAccountKeyed;
    }

    /**
     * Resolves which layout serves this caller and which one mirrors.
     *
     * <p>Note the fallback: when reads have been flipped but the sub has no accountId, this
     * still returns the sub-keyed layout as authoritative. By Phase 3 the backfill has minted
     * an identity for everyone, so it should never fire — but "should never" is not a reason
     * to serve an empty history to someone the migration missed.
     */
    protected Sides<S> sides(String userId) {
        Optional<String> accountId = resolver.resolve(userId);
        if (readAccountKeyed && accountId.isPresent()) {
            return new Sides<>(accountKeyed, accountId.get(), subKeyed, userId);
        }
        return new Sides<>(subKeyed, userId, accountKeyed, accountId.orElse(null));
    }

    /**
     * The two layouts for one caller, already resolved.
     *
     * @param mirrorKey null when there is no second layout to write — an unmigrated user
     *                  before the flip. {@link #alsoWrite} is a no-op in that case.
     */
    protected record Sides<S>(S authoritative, String key, S mirror, String mirrorKey) {

        /**
         * Mirrors a write. Failures are logged and absorbed by
         * {@link DualWriteSupport#mirror}: a mirror that cannot be written costs a row the
         * parity check will flag, whereas letting it throw costs a user their data over a
         * migration they never asked for.
         */
        void alsoWrite(String op, BiConsumer<S, String> action) {
            if (mirrorKey == null) {
                return;
            }
            DualWriteSupport.mirror(op, () -> action.accept(mirror, mirrorKey));
        }
    }
}
