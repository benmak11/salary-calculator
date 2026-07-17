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
            return new ArrayList<>(grants.values());
        }
    }

    @Override
    public RsuGrant create(String userId, RsuGrant grant) {
        grant.setId("g_" + UUID.randomUUID().toString().substring(0, 8));
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
            if (!grants.containsKey(grantId)) return Optional.empty();
            grant.setId(grantId);
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
