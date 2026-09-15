package app.salary.api.store;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.Optional;

/**
 * Resolves a provider sub to its accountId, cached.
 *
 * <p>The B-1b dual-write needs an accountId on every write, and the session token carries
 * only the provider sub. The two ways to get one were a lookup per write or a new token
 * claim; this is the lookup, made cheap.
 *
 * <p><b>Why not a token claim.</b> Session tokens last 30 days, so a claim would not be
 * present on existing sessions for a month and would need this lookup as a fallback anyway —
 * strictly more work for the same result. It would also put an account identifier into a
 * credential stored on the device, for no benefit.
 *
 * <p><b>Why caching is safe here.</b> The sub-to-accountId mapping is written once, when the
 * identity is first minted, and never changes afterwards. There is no invalidation problem,
 * which is the usual reason to be wary of a cache in front of a lookup. A <em>miss</em> is
 * deliberately not cached: an account created moments after a failed lookup must be visible
 * immediately, and negative caching would pin the absence for the whole TTL.
 *
 * <p>The TTL exists only to bound memory on a long-running instance, not for correctness.
 */
public class AccountIdResolver {

    /** Generous: the mapping is immutable, so this only caps how long a dead entry lingers. */
    private static final Duration TTL = Duration.ofHours(12);
    private static final int MAX_ENTRIES = 10_000;

    private final AccountDirectory accounts;
    private final Cache<String, String> subToAccountId;

    public AccountIdResolver(AccountDirectory accounts) {
        this.accounts = accounts;
        this.subToAccountId = Caffeine.newBuilder()
                .expireAfterWrite(TTL)
                .maximumSize(MAX_ENTRIES)
                .build();
    }

    /**
     * Empty when the sub has no identity record yet — the common case for anyone whose last
     * sign-in predates the identity schema, and the reason the dual-write has to tolerate
     * having no accountId rather than treating it as an error.
     */
    public Optional<String> resolve(String providerSub) {
        if (accounts == null || providerSub == null || providerSub.isBlank()) {
            return Optional.empty();
        }
        String cached = subToAccountId.getIfPresent(providerSub);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<String> found = accounts.findAccountIdBySub(providerSub);
        found.ifPresent(id -> subToAccountId.put(providerSub, id));
        return found;
    }

    /** Drops a mapping. Called by account deletion so a purged sub cannot resolve afterwards. */
    public void forget(String providerSub) {
        if (providerSub != null) {
            subToAccountId.invalidate(providerSub);
        }
    }
}
