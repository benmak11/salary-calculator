package app.salary.api.store;

import app.salary.common.dto.CalculateRequest;
import app.salary.common.dto.CalculateResponse;
import app.salary.common.dto.CalculationListResponse;
import app.salary.common.dto.SavedCalculationDetail;
import app.salary.common.dto.SavedCalculationSummary;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 * Firestore-backed {@link CalculationStore}.
 *
 * Layout: {@code users/{userId}/calculations/{calcId}}, with each doc carrying
 * the denormalized {@code summary} map (so list queries don't deserialize the
 * full payload), plus the original {@code request} and {@code response} for
 * the detail view.
 */
public class FirestoreCalculationStore implements CalculationStore {
    private static final TypeReference<Map<String, Object>> MAP_REF = new TypeReference<>() {};

    private final Firestore firestore;
    private final ObjectMapper mapper;

    public FirestoreCalculationStore(Firestore firestore, ObjectMapper mapper) {
        this.firestore = firestore;
        this.mapper = mapper;
    }

    @Override
    public SavedCalculationSummary save(String userId, CalculateRequest request, CalculateResponse response) {
        DocumentReference doc = userCalculations(userId).document();
        String calcId = doc.getId();
        Instant savedAt = Instant.now();
        SavedCalculationSummary summary = CalculationSummarizer.summarize(calcId, savedAt, request, response);

        Map<String, Object> data = new HashMap<>();
        data.put(StoreConstants.FIELD_ID, calcId);
        data.put(StoreConstants.FIELD_CREATED_AT, Timestamp.ofTimeSecondsAndNanos(savedAt.getEpochSecond(), savedAt.getNano()));
        data.put(StoreConstants.FIELD_SUMMARY, mapper.convertValue(summary, MAP_REF));
        data.put(StoreConstants.FIELD_REQUEST, mapper.convertValue(request, MAP_REF));
        data.put(StoreConstants.FIELD_RESPONSE, mapper.convertValue(response, MAP_REF));

        try {
            doc.set(data).get();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Firestore save interrupted", ie);
        } catch (ExecutionException e) {
            throw new RuntimeException("Firestore save failed", e);
        }
        return summary;
    }

    @Override
    public CalculationListResponse list(String userId, int limit, String cursor) {
        int safeLimit = Math.clamp(limit, 1, 100);
        Query q = userCalculations(userId)
                .orderBy(StoreConstants.FIELD_CREATED_AT, Query.Direction.DESCENDING)
                .limit(safeLimit);
        try {
            List<QueryDocumentSnapshot> snaps = q.get().get().getDocuments();
            List<SavedCalculationSummary> items = new ArrayList<>(snaps.size());
            for (QueryDocumentSnapshot snap : snaps) {
                items.add(readSummary(snap));
            }
            return new CalculationListResponse(items, null);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Firestore list interrupted", ie);
        } catch (ExecutionException e) {
            throw new RuntimeException("Firestore list failed", e);
        }
    }

    @Override
    public Optional<SavedCalculationDetail> get(String userId, String calcId) {
        try {
            DocumentSnapshot snap = userCalculations(userId).document(calcId).get().get();
            if (!snap.exists())
                return Optional.empty();
            SavedCalculationSummary summary = readSummary(snap);
            CalculateRequest request = readSubduct(snap, StoreConstants.FIELD_REQUEST, CalculateRequest.class);
            CalculateResponse response = readSubduct(snap, StoreConstants.FIELD_RESPONSE, CalculateResponse.class);
            return Optional.of(new SavedCalculationDetail(summary, request, response));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Firestore get interrupted", ie);
        } catch (ExecutionException e) {
            throw new RuntimeException("Firestore get failed", e);
        }
    }

    @Override
    public boolean delete(String userId, String calcId) {
        DocumentReference doc = userCalculations(userId).document(calcId);
        try {
            DocumentSnapshot snap = doc.get().get();
            if (!snap.exists())
                return false;
            doc.delete().get();
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Firestore delete interrupted", ie);
        } catch (ExecutionException e) {
            throw new RuntimeException("Firestore delete failed", e);
        }
    }

    @Override
    public int deleteAll(String userId) {
        try {
            List<QueryDocumentSnapshot> snaps = userCalculations(userId).get().get().getDocuments();
            for (QueryDocumentSnapshot snap : snaps) {
                snap.getReference().delete().get();
            }
            return snaps.size();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Firestore deleteAll interrupted", ie);
        } catch (ExecutionException e) {
            throw new RuntimeException("Firestore deleteAll failed", e);
        }
    }

    private CollectionReference userCalculations(String userId) {
        return firestore.collection(StoreConstants.USERS).document(userId).collection(StoreConstants.CALCULATIONS);
    }

    private SavedCalculationSummary readSummary(DocumentSnapshot snap) {
        Object raw = snap.get(StoreConstants.FIELD_SUMMARY);
        if (raw == null) {
            SavedCalculationSummary empty = new SavedCalculationSummary();
            empty.setId(snap.getId());
            return empty;
        }
        return mapper.convertValue(raw, SavedCalculationSummary.class);
    }

    private <T> T readSubduct(DocumentSnapshot snap, String field, Class<T> type) {
        Object raw = snap.get(field);
        return raw == null ? null : mapper.convertValue(raw, type);
    }
}
