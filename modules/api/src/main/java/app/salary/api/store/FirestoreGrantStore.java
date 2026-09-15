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
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
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
    private static final TypeReference<Map<String, Object>> MAP_REF = new TypeReference<>() {};

    private final Firestore firestore;
    private final ObjectMapper mapper;

    private final StoreLayout layout;

    public FirestoreGrantStore(Firestore firestore, ObjectMapper mapper) {
        this(firestore, mapper, StoreLayout.SUB_KEYED);
    }

    public FirestoreGrantStore(Firestore firestore, ObjectMapper mapper, StoreLayout layout) {
        this.firestore = firestore;
        this.mapper = mapper;
        this.layout = layout;
    }

    @Override
    public List<RsuGrant> list(String userId) {
        Query q = userGrants(userId).orderBy(StoreConstants.FIELD_CREATED_AT, Query.Direction.ASCENDING);
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
        grant.setId(userGrants(userId).document().getId());
        grant.setCreatedAt(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        return put(userId, grant);
    }

    @Override
    public RsuGrant put(String userId, RsuGrant grant) {
        DocumentReference doc = userGrants(userId).document(grant.getId());
        // The timestamp travels with the grant: a mirror of an edited grant, or a backfilled
        // one, must land with its ORIGINAL createdAt or it lists in the wrong position.
        Instant createdAt = parseCreatedAt(grant.getCreatedAt());
        if (createdAt == null) {
            createdAt = Instant.now();
            grant.setCreatedAt(DateTimeFormatter.ISO_INSTANT.format(createdAt));
        }
        try {
            doc.set(toDoc(grant, createdAt)).get();
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
            Object stored = snap.get(StoreConstants.FIELD_CREATED_AT);
            data.put(StoreConstants.FIELD_CREATED_AT, stored);
            doc.set(data).get();
            // Put it on the returned DTO too: the dual-write mirror writes what is returned,
            // and without this the account-keyed copy would get a fresh timestamp and jump
            // to the end of the list.
            grant.setCreatedAt(formatCreatedAt(stored));
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

    private CollectionReference userGrants(String key) {
        return switch (layout) {
            case SUB_KEYED -> firestore.collection(StoreConstants.USERS)
                    .document(key).collection(StoreConstants.GRANTS);
            case ACCOUNT_KEYED -> firestore.collection(StoreConstants.GRANTS)
                    .document(key).collection(StoreConstants.ENTRIES);
        };
    }

    private Map<String, Object> toDoc(RsuGrant grant, Instant createdAt) {
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> body = mapper.convertValue(grant, MAP_REF);
        // Stored once, as the ordering field on the document, not again inside the payload.
        body.remove("createdAt");
        data.put(StoreConstants.FIELD_GRANT, body);
        if (createdAt != null) {
            data.put(StoreConstants.FIELD_CREATED_AT,
                    Timestamp.ofTimeSecondsAndNanos(createdAt.getEpochSecond(), createdAt.getNano()));
        }
        return data;
    }

    private RsuGrant readGrant(DocumentSnapshot snap) {
        Object raw = snap.get(StoreConstants.FIELD_GRANT);
        if (raw == null) return null;
        RsuGrant grant = mapper.convertValue(raw, RsuGrant.class);
        grant.setCreatedAt(formatCreatedAt(snap.get(StoreConstants.FIELD_CREATED_AT)));
        return grant;
    }

    private static String formatCreatedAt(Object stored) {
        if (stored instanceof Timestamp ts) {
            return DateTimeFormatter.ISO_INSTANT.format(
                    Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos()));
        }
        return null;
    }

    /** Lenient: a malformed value falls back to "now" in the caller rather than failing a save. */
    private static Instant parseCreatedAt(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            return Instant.parse(iso);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
