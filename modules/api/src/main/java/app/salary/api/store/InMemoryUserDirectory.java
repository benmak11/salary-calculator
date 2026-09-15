package app.salary.api.store;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Test + local-dev (no GCP) {@link UserDirectory} backed by an in-memory map. */
public class InMemoryUserDirectory implements UserDirectory {
    private final Clock clock;
    private final Map<String, Entry> users = new ConcurrentHashMap<>();

    public InMemoryUserDirectory() { this(Clock.systemUTC()); }
    public InMemoryUserDirectory(Clock clock) { this.clock = clock; }

    @Override
    public void upsertOnSignIn(String userId, String displayName) {
        users.compute(userId, (id, existing) -> {
            Instant now = clock.instant();
            if (existing == null) {
                return new Entry(displayName, now, now);
            }
            String name = (existing.displayName != null) ? existing.displayName : displayName;
            return new Entry(name, existing.createdAt, now);
        });
    }

    @Override
    public Optional<String> displayName(String userId) {
        Entry e = users.get(userId);
        return (e == null || e.displayName == null) ? Optional.empty() : Optional.of(e.displayName);
    }

    @Override
    public void delete(String userId) {
        users.remove(userId);
    }

    private record Entry(String displayName, Instant createdAt, Instant lastSeenAt) {}

    @Override
    public List<String> listUserIds(String afterUserId, int limit) {
        return users.keySet().stream()
                .sorted()
                .filter(id -> afterUserId == null || id.compareTo(afterUserId) > 0)
                .limit(Math.max(0, limit))
                .toList();
    }
}
