package app.salary.api.store;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Test + local-dev (no GCP) {@link EventStore}. Unbounded, so not for production use. */
public class InMemoryEventStore implements EventStore {
    private final List<EventRecord> events = new CopyOnWriteArrayList<>();

    @Override
    public int append(List<EventRecord> batch) {
        events.addAll(batch);
        return batch.size();
    }

    /** Everything appended so far, in arrival order. */
    public List<EventRecord> all() {
        return List.copyOf(events);
    }
}
