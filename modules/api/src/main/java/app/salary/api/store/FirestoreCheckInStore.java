package app.salary.api.store;

import app.salary.common.dto.PaycheckCheckIn;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 * Firestore-backed {@link CheckInStore}.
 *
 * <p>Layout: {@code checkIns/{accountId}/entries/{payDate}}. <b>The pay date is the document
 * id</b>, which makes "one check-in per payday" a property of the schema rather than
 * something a query has to enforce — a correction overwrites in place and can never
 * double-count in a YTD total.
 *
 * <p>Keyed on accountId, not provider sub, so the pending B-1b migration never has to touch
 * this collection.
 */
public class FirestoreCheckInStore implements CheckInStore {
    private static final String CHECK_INS = "checkIns";
    private static final String ENTRIES = "entries";
    private static final String FIELD_CHECK_IN = "checkIn";
    private static final String FIELD_PAY_DATE = "payDate";
    private static final TypeReference<Map<String, Object>> MAP_REF = new TypeReference<>() {};

    private final Firestore firestore;
    private final ObjectMapper mapper;

    public FirestoreCheckInStore(Firestore firestore, ObjectMapper mapper) {
        this.firestore = firestore;
        this.mapper = mapper;
    }

    @Override
    public PaycheckCheckIn save(String accountId, PaycheckCheckIn checkIn) {
        DocumentReference doc = entries(accountId).document(checkIn.getPayDate());
        try {
            // Keep the id stable across a correction so any client holding it stays valid.
            DocumentSnapshot existing = doc.get().get();
            String existingId = existing.exists() ? readId(existing) : null;
            checkIn.setId(existingId != null ? existingId : Ulid.generate());

            Map<String, Object> payload = new HashMap<>();
            payload.put(FIELD_CHECK_IN, mapper.convertValue(checkIn, MAP_REF));
            payload.put(FIELD_PAY_DATE, checkIn.getPayDate());
            doc.set(payload).get();
            return checkIn;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Firestore check-in save interrupted", ie);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Firestore check-in save failed", e);
        }
    }

    @Override
    public List<PaycheckCheckIn> list(String accountId, int limit) {
        Query q = entries(accountId)
                .orderBy(FIELD_PAY_DATE, Query.Direction.DESCENDING)
                .limit(limit);
        try {
            List<QueryDocumentSnapshot> snaps = q.get().get().getDocuments();
            List<PaycheckCheckIn> items = new ArrayList<>(snaps.size());
            for (QueryDocumentSnapshot snap : snaps) {
                PaycheckCheckIn entry = read(snap);
                if (entry != null)
                    items.add(entry);
            }
            return items;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Firestore check-in list interrupted", ie);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Firestore check-in list failed", e);
        }
    }

    @Override
    public Optional<PaycheckCheckIn> find(String accountId, String id) {
        // Ids are ULIDs, not the document key, so this is a scan of one account's entries.
        // Acceptable: a person has a few dozen paydays a year, and list() is the hot read.
        return list(accountId, Integer.MAX_VALUE).stream()
                .filter(c -> id.equals(c.getId()))
                .findFirst();
    }

    @Override
    public boolean delete(String accountId, String id) {
        Optional<PaycheckCheckIn> found = find(accountId, id);
        if (found.isEmpty())
            return false;
        try {
            entries(accountId).document(found.get().getPayDate()).delete().get();
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Firestore check-in delete interrupted", ie);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Firestore check-in delete failed", e);
        }
    }

    @Override
    public int deleteAll(String accountId) {
        try {
            List<QueryDocumentSnapshot> snaps = entries(accountId).get().get().getDocuments();
            for (QueryDocumentSnapshot snap : snaps) {
                snap.getReference().delete().get();
            }
            return snaps.size();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Firestore check-in purge interrupted", ie);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Firestore check-in purge failed", e);
        }
    }

    private CollectionReference entries(String accountId) {
        return firestore.collection(CHECK_INS).document(accountId).collection(ENTRIES);
    }

    private String readId(DocumentSnapshot snap) {
        PaycheckCheckIn entry = read(snap);
        return entry == null ? null : entry.getId();
    }

    @SuppressWarnings("unchecked")
    private PaycheckCheckIn read(DocumentSnapshot snap) {
        Object raw = snap.get(FIELD_CHECK_IN);
        if (!(raw instanceof Map<?, ?> map)) {
            return null;
        }
        return mapper.convertValue(map, PaycheckCheckIn.class);
    }
}
