package app.salary.api.store;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Test + local-dev (no GCP) {@link AccountDirectory} backed by in-memory maps. */
public class InMemoryAccountDirectory implements AccountDirectory {
    private final Clock clock;
    private final Map<String, Account> accounts = new ConcurrentHashMap<>();
    private final Map<String, Identity> identities = new ConcurrentHashMap<>();
    private final java.util.Set<String> legacyProBudget = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public InMemoryAccountDirectory() { this(Clock.systemUTC()); }
    public InMemoryAccountDirectory(Clock clock) { this.clock = clock; }

    @Override
    public String resolveOrCreate(String provider, String providerSub, String displayName) {
        String key = identityKey(provider, providerSub);
        Instant now = clock.instant();

        Identity identity = identities.compute(key, (k, existing) -> existing != null
                ? new Identity(existing.provider, existing.sub, existing.accountId, existing.createdAt, now)
                : new Identity(provider, providerSub, Ulid.generate(clock.millis()), now, now));

        accounts.compute(identity.accountId, (id, existing) -> {
            if (existing == null) {
                return new Account(id, displayName, now, now);
            }
            String name = existing.displayName != null ? existing.displayName : displayName;
            return new Account(id, name, existing.createdAt, now);
        });

        return identity.accountId;
    }

    @Override
    public Optional<String> findAccountId(String provider, String providerSub) {
        Identity identity = identities.get(identityKey(provider, providerSub));
        return identity == null ? Optional.empty() : Optional.of(identity.accountId);
    }

    @Override
    public Optional<String> findAccountIdBySub(String providerSub) {
        return identities.values().stream()
                .filter(i -> i.sub.equals(providerSub))
                .map(Identity::accountId)
                .findFirst();
    }

    @Override
    public boolean hasLegacyProBudget(String accountId) {
        return accountId != null && legacyProBudget.contains(accountId);
    }

    /**
     * Test seam standing in for the B1 migration's one-time backfill. Nothing in production
     * calls this; the flag is written by the migration, not by a running request.
     */
    public void grantLegacyProBudget(String accountId) {
        legacyProBudget.add(accountId);
    }

    @Override
    public Optional<String> relinkIdentity(String providerSub, String targetAccountId) {
        for (Map.Entry<String, Identity> entry : identities.entrySet()) {
            Identity identity = entry.getValue();
            if (!identity.sub.equals(providerSub) || identity.accountId.equals(targetAccountId)) {
                continue;
            }
            String previous = identity.accountId;
            identities.put(entry.getKey(), new Identity(identity.provider, identity.sub,
                    targetAccountId, identity.createdAt, identity.lastSeenAt));
            return Optional.of(previous);
        }
        return Optional.empty();
    }

    @Override
    public int deleteByProviderSub(String providerSub) {
        Optional<String> accountId = findAccountIdBySub(providerSub);
        if (accountId.isEmpty()) {
            return 0;
        }

        List<String> keys = new ArrayList<>();
        identities.forEach((key, identity) -> {
            if (identity.accountId.equals(accountId.get())) {
                keys.add(key);
            }
        });
        keys.forEach(identities::remove);
        accounts.remove(accountId.get());
        legacyProBudget.remove(accountId.get());
        return keys.size();
    }

    private static String identityKey(String provider, String providerSub) {
        return provider + ":" + providerSub;
    }

    private record Account(String id, String displayName, Instant createdAt, Instant lastSeenAt) {}

    private record Identity(String provider, String sub, String accountId,
                            Instant createdAt, Instant lastSeenAt) {}
}
