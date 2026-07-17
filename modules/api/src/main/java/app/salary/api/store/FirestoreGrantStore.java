package app.salary.api.store;

import app.salary.common.dto.RsuGrant;
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
 * Firestore-backed {@link GrantStore}.
 *
 * Layout: {@code users/{userId}/grants/{grantId}} with the grant serialized under
 * {@code grant} plus a {@code createdAt} timestamp for oldest-first list ordering.
 */
public class FirestoreGrantStore implements GrantStore {
    private static final String USERS = "users";
    private static final String GRANTS = "grants";
    private static final String FIELD_GRANT = "grant";
    private static final String FIELD_CREATED_AT = "createdAt";
    private static final TypeReference<Map<String, Object>> MAP_REF = new TypeReference<>() {};

    private final Firestore firestore;
    private final ObjectMapper mapper;

    public FirestoreGrantStore(Firestore firestore, ObjectMapper mapper) {
        this.firestore = firestore;
        this.mapper = mapper;
    }

    @Override
    public List<RsuGrant> list(String userId) {
        Query q = userGrants(userId).orderBy(FIELD_CREATED_AT, Query.Direction.ASCENDING);
        try {
            List<QueryDocumentSnapshot> snaps = q.get().get().getDocuments();
            List<RsuGrant> items = new ArrayList<>(snaps.size());
            for (QueryDocumentSnapshot snap : snaps) {
                RsuGrant grant = readGrant(snap);
                if (grant != null) items.add(grant);
            }
            return items;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Firestore grant list interrupted", ie);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Firestore grant list failed", e);
        }
    }

    @Override
    public RsuGrant create(String userId, RsuGrant grant) {
        DocumentReference doc = userGrants(userId).document();
        grant.setId(doc.getId());
        try {
            doc.set(toDoc(grant, Instant.now())).get();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Firestore grant create interrupted", ie);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Firestore grant create failed", e);
        }
        return grant;
    }

    @Override
    public Optional<RsuGrant> update(String userId, String grantId, RsuGrant grant) {
        DocumentReference doc = userGrants(userId).document(grantId);
        try {
            DocumentSnapshot snap = doc.get().get();
            if (!snap.exists()) return Optional.empty();
            grant.setId(grantId);
            // Preserve createdAt so list order stays stable across edits
            Map<String, Object> data = toDoc(grant, null);
            data.put(FIELD_CREATED_AT, snap.get(FIELD_CREATED_AT));
            doc.set(data).get();
            return Optional.of(grant);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Firestore grant update interrupted", ie);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Firestore grant update failed", e);
        }
    }

    @Override
    public boolean delete(String userId, String grantId) {
        DocumentReference doc = userGrants(userId).document(grantId);
        try {
            DocumentSnapshot snap = doc.get().get();
            if (!snap.exists()) return false;
            doc.delete().get();
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Firestore grant delete interrupted", ie);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Firestore grant delete failed", e);
        }
    }

    @Override
    public int deleteAll(String userId) {
        try {
            List<QueryDocumentSnapshot> snaps = userGrants(userId).get().get().getDocuments();
            for (QueryDocumentSnapshot snap : snaps) {
                snap.getReference().delete().get();
            }
            return snaps.size();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Firestore grant deleteAll interrupted", ie);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Firestore grant deleteAll failed", e);
        }
    }

    private CollectionReference userGrants(String userId) {
        return firestore.collection(USERS).document(userId).collection(GRANTS);
    }

    private Map<String, Object> toDoc(RsuGrant grant, Instant createdAt) {
        Map<String, Object> data = new HashMap<>();
        data.put(FIELD_GRANT, mapper.convertValue(grant, MAP_REF));
        if (createdAt != null) {
            data.put(FIELD_CREATED_AT,
                    Timestamp.ofTimeSecondsAndNanos(createdAt.getEpochSecond(), createdAt.getNano()));
        }
        return data;
    }

    private RsuGrant readGrant(DocumentSnapshot snap) {
        Object raw = snap.get(FIELD_GRANT);
        return raw == null ? null : mapper.convertValue(raw, RsuGrant.class);
    }
}
