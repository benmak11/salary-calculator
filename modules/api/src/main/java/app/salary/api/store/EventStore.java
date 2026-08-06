package app.salary.api.store;

import java.util.List;

/** Append-only sink for analytics events. */
public interface EventStore {
    /** Appends a batch and returns how many events were written. */
    int append(List<EventRecord> events);
}
