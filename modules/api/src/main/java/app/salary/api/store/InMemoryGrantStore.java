package app.salary.api.store;

import app.salary.common.dto.RsuGrant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Map-backed {@link GrantStore} for tests and bootless dev environments.
 * Insertion order doubles as the oldest-first list order.
 */
public class InMemoryGrantStore implements GrantStore {
    private final Map<String, Map<String, RsuGrant>> byUser = new ConcurrentHashMap<>();

    @Override
    public List<RsuGrant> list(String userId) {
        Map<String, RsuGrant> grants = byUser.get(userId);
        if (grants == null) return List.of();
        synchronized (grants) {
            // Ordered on createdAt like the Firestore store, so an ordering divergence between
            // the two B-1b layouts is something a test against this store can actually see.
            List<RsuGrant> items = new ArrayList<>(grants.values());
            items.sort(java.util.Comparator.comparing(
                    g -> g.getCreatedAt() == null ? "" : g.getCreatedAt()));
            return items;
        }
    }

    @Override
    public RsuGrant create(String userId, RsuGrant grant) {
        grant.setId("g_" + UUID.randomUUID().toString().substring(0, 8));
        grant.setCreatedAt(java.time.format.DateTimeFormatter.ISO_INSTANT.format(java.time.Instant.now()));
        return put(userId, grant);
    }

    @Override
    public RsuGrant put(String userId, RsuGrant grant) {
        if (grant.getCreatedAt() == null || grant.getCreatedAt().isBlank()) {
            grant.setCreatedAt(java.time.format.DateTimeFormatter.ISO_INSTANT.format(java.time.Instant.now()));
        }
        Map<String, RsuGrant> grants = byUser.computeIfAbsent(userId, k -> new LinkedHashMap<>());
        synchronized (grants) {
            grants.put(grant.getId(), grant);
        }
        return grant;
    }

    @Override
    public Optional<RsuGrant> update(String userId, String grantId, RsuGrant grant) {
        Map<String, RsuGrant> grants = byUser.get(userId);
        if (grants == null) return Optional.empty();
        synchronized (grants) {
            RsuGrant existing = grants.get(grantId);
            if (existing == null) return Optional.empty();
            grant.setId(grantId);
            // Preserve createdAt across edits, as the Firestore store does.
            grant.setCreatedAt(existing.getCreatedAt());
            grants.put(grantId, grant);
            return Optional.of(grant);
        }
    }

    @Override
    public boolean delete(String userId, String grantId) {
        Map<String, RsuGrant> grants = byUser.get(userId);
        if (grants == null) return false;
        synchronized (grants) {
            return grants.remove(grantId) != null;
        }
    }

    @Override
    public int deleteAll(String userId) {
        Map<String, RsuGrant> grants = byUser.remove(userId);
        if (grants == null) return 0;
        synchronized (grants) {
            return grants.size();
        }
    }
}
