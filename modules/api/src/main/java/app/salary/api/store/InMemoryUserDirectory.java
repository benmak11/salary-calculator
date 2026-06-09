package app.salary.api.store;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Test + local-dev (no GCP) {@link UserDirectory} backed by an in-memory map. */
public class InMemoryUserDirectory implements UserDirectory {
    private final Clock clock;
    private final Map<String, Entry> users = new ConcurrentHashMap<>();

    public InMemoryUserDirectory() { this(Clock.systemUTC()); }
    public InMemoryUserDirectory(Clock clock) { this.clock = clock; }

    @Override
    public void upsertOnSignIn(String userId, String displayName, String email) {
        users.compute(userId, (id, existing) -> {
            Instant now = clock.instant();
            if (existing == null) {
                return new Entry(displayName, email, now, now);
            }
            String name = (existing.displayName != null) ? existing.displayName : displayName;
            String e = (existing.email != null) ? existing.email : email;
            return new Entry(name, e, existing.createdAt, now);
        });
    }

    @Override
    public Optional<String> displayName(String userId) {
        Entry e = users.get(userId);
        return (e == null || e.displayName == null) ? Optional.empty() : Optional.of(e.displayName);
    }

    private record Entry(String displayName, String email, Instant createdAt, Instant lastSeenAt) {}
}
