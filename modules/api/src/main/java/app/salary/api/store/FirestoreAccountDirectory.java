package app.salary.api.store;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.SetOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 * Firestore-backed {@link AccountDirectory}.
 *
 * <p>Layout: {@code accounts/{accountId}} and {@code identities/{provider}:{sub}}. The
 * identity document id is the composite key rather than a generated id so the mapping is
 * a point read — no query, no index, and no chance of two documents claiming the same
 * identity.
 */
public class FirestoreAccountDirectory implements AccountDirectory {
    private static final Logger log = LoggerFactory.getLogger(FirestoreAccountDirectory.class);

    private final Firestore firestore;

    public FirestoreAccountDirectory(Firestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public String resolveOrCreate(String provider, String providerSub, String displayName) {
        DocumentReference identityRef =
                firestore.collection(StoreConstants.IDENTITIES).document(identityKey(provider, providerSub));
        try {
            // A transaction, not a read-then-write: two devices signing in at once would
            // otherwise both see "no identity" and mint two accounts for the same person.
            return firestore.runTransaction(tx -> {
                DocumentSnapshot existing = tx.get(identityRef).get();
                String id = existing.exists() ? existing.getString(StoreConstants.FIELD_ACCOUNT_ID) : null;
                boolean fresh = (id == null || id.isBlank());
                if (fresh) {
                    id = Ulid.generate();
                }

                Map<String, Object> identityPatch = new HashMap<>();
                identityPatch.put("provider", provider);
                identityPatch.put(StoreConstants.FIELD_SUB, providerSub);
                identityPatch.put(StoreConstants.FIELD_ACCOUNT_ID, id);
                identityPatch.put(StoreConstants.FIELD_LAST_SEEN_AT, Timestamp.now());
                if (fresh) {
                    identityPatch.put(StoreConstants.FIELD_CREATED_AT, Timestamp.now());
                }
                tx.set(identityRef, identityPatch, SetOptions.merge());

                Map<String, Object> accountPatch = new HashMap<>();
                accountPatch.put(StoreConstants.FIELD_ID, id);
                accountPatch.put(StoreConstants.FIELD_LAST_SEEN_AT, Timestamp.now());
                if (displayName != null && !displayName.isBlank()) {
                    accountPatch.put(StoreConstants.FIELD_DISPLAY_NAME, displayName);
                }
                if (fresh) {
                    accountPatch.put(StoreConstants.FIELD_CREATED_AT, Timestamp.now());
                }
                tx.set(firestore.collection(StoreConstants.ACCOUNTS).document(id), accountPatch, SetOptions.merge());

                return id;
            }).get();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Firestore account resolve interrupted", ie);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Firestore account resolve failed", e);
        }
    }

    @Override
    public Optional<String> findAccountId(String provider, String providerSub) {
        try {
            DocumentSnapshot snap = firestore.collection(StoreConstants.IDENTITIES)
                    .document(identityKey(provider, providerSub)).get().get();
            if (!snap.exists()) {
                return Optional.empty();
            }
            String accountId = snap.getString(StoreConstants.FIELD_ACCOUNT_ID);
            return (accountId == null || accountId.isBlank()) ? Optional.empty() : Optional.of(accountId);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Firestore identity fetch interrupted", ie);
        } catch (ExecutionException e) {
            log.warn("Firestore identity fetch failed for provider={}", provider, e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> findAccountIdBySub(String providerSub) {
        try {
            List<QueryDocumentSnapshot> bySub = identitiesForSub(providerSub);
            if (bySub.isEmpty()) {
                return Optional.empty();
            }
            String accountId = bySub.getFirst().getString(StoreConstants.FIELD_ACCOUNT_ID);
            return (accountId == null || accountId.isBlank()) ? Optional.empty() : Optional.of(accountId);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Firestore identity lookup interrupted", ie);
        } catch (ExecutionException e) {
            log.warn("Firestore identity lookup by sub failed", e);
            return Optional.empty();
        }
    }

    @Override
    public boolean hasLegacyProBudget(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return false;
        }
        try {
            DocumentSnapshot snap = firestore.collection(StoreConstants.ACCOUNTS).document(accountId).get().get();
            return snap.exists() && Boolean.TRUE.equals(snap.getBoolean(StoreConstants.FIELD_LEGACY_PRO_BUDGET));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Firestore account read interrupted", ie);
        } catch (ExecutionException e) {
            // Failing closed: an unreadable account is not a grandfathered one.
            log.warn("Firestore legacy-pro-budget read failed", e);
            return false;
        }
    }

    @Override
    public Optional<String> relinkIdentity(String providerSub, String targetAccountId) {
        try {
            List<QueryDocumentSnapshot> bySub = identitiesForSub(providerSub);
            for (QueryDocumentSnapshot snap : bySub) {
                String previous = snap.getString(StoreConstants.FIELD_ACCOUNT_ID);
                if (previous == null || previous.equals(targetAccountId)) {
                    continue;
                }
                snap.getReference().update(StoreConstants.FIELD_ACCOUNT_ID, targetAccountId).get();
                return Optional.of(previous);
            }
            return Optional.empty();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Firestore identity relink interrupted", ie);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Firestore identity relink failed", e);
        }
    }

    @Override
    public int deleteByProviderSub(String providerSub) {
        try {
            List<QueryDocumentSnapshot> bySub = identitiesForSub(providerSub);
            if (bySub.isEmpty()) {
                return 0;
            }

            String accountId = bySub.getFirst().getString(StoreConstants.FIELD_ACCOUNT_ID);
            if (accountId == null || accountId.isBlank()) {
                for (QueryDocumentSnapshot snap : bySub) {
                    snap.getReference().delete().get();
                }
                return bySub.size();
            }

            // Sweep by accountId, not by sub: a second linked identity would otherwise
            // survive and point at an account document that no longer exists.
            List<QueryDocumentSnapshot> linked = firestore.collection(StoreConstants.IDENTITIES)
                    .whereEqualTo(StoreConstants.FIELD_ACCOUNT_ID, accountId).get().get().getDocuments();
            for (QueryDocumentSnapshot snap : linked) {
                snap.getReference().delete().get();
            }
            firestore.collection(StoreConstants.ACCOUNTS).document(accountId).delete().get();
            return linked.size();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Firestore account delete interrupted", ie);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Firestore account delete failed", e);
        }
    }

    private List<QueryDocumentSnapshot> identitiesForSub(String providerSub)
            throws InterruptedException, ExecutionException {
        return firestore.collection(StoreConstants.IDENTITIES)
                .whereEqualTo(StoreConstants.FIELD_SUB, providerSub).get().get().getDocuments();
    }

    private static String identityKey(String provider, String providerSub) {
        return provider + ":" + providerSub;
    }
}
