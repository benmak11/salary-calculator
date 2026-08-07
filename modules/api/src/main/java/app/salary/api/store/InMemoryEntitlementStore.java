package app.salary.api.store;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link EntitlementStore} for local runs and tests.
 *
 * <p>Keyed accountId to store-name to record, mirroring the Firestore document shape so the
 * "one store's write never disturbs another's" invariant is exercised here too rather than
 * only against live GCP.
 */
public class InMemoryEntitlementStore implements EntitlementStore {

    private final Map<String, Map<String, Entitlement>> byAccount = new ConcurrentHashMap<>();

    @Override
    public void upsert(String accountId, Entitlement entitlement) {
        byAccount.computeIfAbsent(accountId, k -> new ConcurrentHashMap<>())
                .put(entitlement.store(), new Entitlement(
                        entitlement.store(),
                        entitlement.productId(),
                        entitlement.expiresAt(),
                        entitlement.revoked(),
                        Instant.now()));
    }

    @Override
    public List<Entitlement> findAll(String accountId) {
        Map<String, Entitlement> stores = byAccount.get(accountId);
        return stores == null ? List.of() : new ArrayList<>(stores.values());
    }

    @Override
    public void deleteAll(String accountId) {
        byAccount.remove(accountId);
    }
}
