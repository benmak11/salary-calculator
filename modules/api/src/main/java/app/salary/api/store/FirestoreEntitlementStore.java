package app.salary.api.store;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Firestore-backed {@link EntitlementStore}.
 *
 * <p>Layout: {@code entitlements/{accountId}} holding one subdocument per store under a
 * {@code stores} map, so {@code stores.app_store} and {@code stores.play} sit side by side
 * in one document. A single document keeps resolution to one point read, and writing a
 * nested field path means one store's update cannot clobber the other's.
 */
public class FirestoreEntitlementStore implements EntitlementStore {
    private static final Logger log = LoggerFactory.getLogger(FirestoreEntitlementStore.class);
    private static final String ENTITLEMENTS = "entitlements";
    private static final String STORES = "stores";

    private final Firestore firestore;

    public FirestoreEntitlementStore(Firestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public void upsert(String accountId, Entitlement entitlement) {
        DocumentReference ref = firestore.collection(ENTITLEMENTS).document(accountId);
        // update() on a dotted field path touches only that store's subtree. A set() with a
        // stores map — even merging — is the shape that loses the other store's record.
        Map<String, Object> storeRecord = new HashMap<>();
        storeRecord.put("store", entitlement.store());
        storeRecord.put("productId", entitlement.productId());
        storeRecord.put("expiresAt", entitlement.expiresAt() == null
                ? null : Timestamp.ofTimeSecondsAndNanos(
                        entitlement.expiresAt().getEpochSecond(), entitlement.expiresAt().getNano()));
        storeRecord.put("revoked", entitlement.revoked());
        storeRecord.put("updatedAt", Timestamp.now());

        try {
            firestore.runTransaction(tx -> {
                DocumentSnapshot snap = tx.get(ref).get();
                if (snap.exists()) {
                    tx.update(ref, STORES + "." + entitlement.store(), storeRecord);
                } else {
                    tx.set(ref, Map.of("accountId", accountId,
                            STORES, Map.of(entitlement.store(), storeRecord)));
                }
                return null;
            }).get();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Firestore entitlement write interrupted", ie);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Firestore entitlement write failed", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Entitlement> findAll(String accountId) {
        try {
            DocumentSnapshot snap = firestore.collection(ENTITLEMENTS).document(accountId).get().get();
            if (!snap.exists()) {
                return List.of();
            }
            Object raw = snap.get(STORES);
            if (!(raw instanceof Map<?, ?> stores)) {
                return List.of();
            }
            List<Entitlement> out = new ArrayList<>(stores.size());
            for (Map.Entry<?, ?> entry : stores.entrySet()) {
                if (entry.getValue() instanceof Map<?, ?> value) {
                    out.add(toEntitlement(String.valueOf(entry.getKey()), (Map<String, Object>) value));
                }
            }
            return out;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Firestore entitlement read interrupted", ie);
        } catch (ExecutionException e) {
            // Failing closed is the safe direction: an unreadable entitlement is not a grant.
            log.warn("Firestore entitlement read failed", e);
            return List.of();
        }
    }

    @Override
    public void deleteAll(String accountId) {
        try {
            firestore.collection(ENTITLEMENTS).document(accountId).delete().get();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Firestore entitlement delete interrupted", ie);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Firestore entitlement delete failed", e);
        }
    }

    private static Entitlement toEntitlement(String store, Map<String, Object> value) {
        return new Entitlement(
                store,
                asString(value.get("productId")),
                asInstant(value.get("expiresAt")),
                Boolean.TRUE.equals(value.get("revoked")),
                asInstant(value.get("updatedAt")));
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Instant asInstant(Object o) {
        if (o instanceof Timestamp ts) {
            return ts.toDate().toInstant();
        }
        return null;
    }
}
