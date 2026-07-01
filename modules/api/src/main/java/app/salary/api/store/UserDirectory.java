package app.salary.api.store;

import java.util.Optional;

/**
 * Tracks the set of users who have ever signed in. Kept deliberately tiny — name +
 * created/last-seen timestamps. We never persist the Apple email (Apple may
 * relay/hide it; using it as a user identifier is fragile), so it is not accepted
 * here at all.
 */
public interface UserDirectory {
    /** Idempotent: creates the user doc on first call, updates lastSeenAt on subsequent calls. */
    void upsertOnSignIn(String userId, String displayName);

    /** Display name we have on file (Apple only supplies this on the first sign-in). */
    Optional<String> displayName(String userId);

    /** Removes the user's directory record. No-op when the user doesn't exist. */
    void delete(String userId);
}
