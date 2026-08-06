package app.salary.api.store;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteBatch;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Firestore-backed {@link EventStore}.
 *
 * <p>Flat {@code events} collection with ULID document ids, so a console listing reads in
 * arrival order without needing an index. One {@link WriteBatch} per request: the
 * controller caps a batch at 50 events, comfortably inside Firestore's 500-operation
 * limit.
 */
public class FirestoreEventStore implements EventStore {
    private static final String COLLECTION = "events";

    private final Firestore firestore;

    public FirestoreEventStore(Firestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public int append(List<EventRecord> events) {
        if (events.isEmpty()) {
            return 0;
        }
        WriteBatch batch = firestore.batch();
        for (EventRecord event : events) {
            batch.set(firestore.collection(COLLECTION).document(event.id()), toDoc(event));
        }
        try {
            batch.commit().get();
            return events.size();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Firestore event append interrupted", ie);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Firestore event append failed", e);
        }
    }

    private static Map<String, Object> toDoc(EventRecord event) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("name", event.name());
        doc.put("deviceId", event.deviceId());
        doc.put("occurredAt", toTimestamp(event.occurredAt()));
        doc.put("receivedAt", toTimestamp(event.receivedAt()));
        if (event.accountId() != null) {
            doc.put("accountId", event.accountId());
        }
        if (event.client() != null) {
            doc.put("client", event.client());
        }
        if (event.properties() != null && !event.properties().isEmpty()) {
            doc.put("properties", event.properties());
        }
        return doc;
    }

    private static Timestamp toTimestamp(java.time.Instant instant) {
        return Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano());
    }
}
