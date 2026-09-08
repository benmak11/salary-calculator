package app.salary.api.store;

import java.util.List;

/**
 * Sink for analytics events. Append-only in normal operation; the one exception is
 * account deletion.
 */
public interface EventStore {
    /** Appends a batch and returns how many events were written. */
    int append(List<EventRecord> events);

    /**
     * Removes every event attributed to an account. Used by account deletion, which the
     * privacy policy commits to in writing.
     *
     * <p><b>This cannot be complete, and that is inherent rather than a shortcut.</b> Events
     * carry an accountId only when the sender was signed in at the time. A signed-out run is
     * attributed to its deviceId alone, and nothing links that device back to an account
     * after the fact, so pre-sign-in events survive deletion. The alternative — recording the
     * device-to-account link so it could be reversed later — would mean building exactly the
     * cross-session identity trail the anonymous ingest exists to avoid.
     */
    int deleteAll(String accountId);
}
