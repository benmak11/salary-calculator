package app.salary.api.store;

import java.util.Optional;

/**
 * Tracks the set of users who have ever signed in. Kept deliberately tiny — name +
 * created/last-seen timestamps. We don't store the Apple email anywhere persistent
 * by design (Apple may relay/hide it; using it as a user identifier is fragile).
 */
public interface UserDirectory {
    /** Idempotent: creates the user doc on first call, updates lastSeenAt on subsequent calls. */
    void upsertOnSignIn(String userId, String displayName, String email);

    /** Display name we have on file (Apple only supplies this on the first sign-in). */
    Optional<String> displayName(String userId);
}
