package app.salary.api.store;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 * Firestore-backed {@link LinkCodeStore}.
 *
 * <p>Layout: {@code linkCodes/{code}} — the code itself is the document id, so redemption is
 * a point read and two accounts can never hold the same live code (the second write to an
 * occupied id is detected rather than silently creating a duplicate).
 *
 * <p>Expiry is enforced in code rather than by a Firestore TTL policy. A TTL policy deletes
 * "within 24 hours" of the timestamp, which is useless for a ten-minute secret; the policy is
 * still worth adding later purely as garbage collection.
 */
public class FirestoreLinkCodeStore implements LinkCodeStore {
    private static final Logger log = LoggerFactory.getLogger(FirestoreLinkCodeStore.class);
    private static final String LINK_CODES = "linkCodes";
    private static final String FIELD_ACCOUNT_ID = "accountId";
    private static final String FIELD_ATTEMPTS = "attempts";
    private static final String FIELD_REDEEMED = "redeemed";
    private static final String FIELD_EXPIRES_AT = "expiresAt";
    private static final String FIELD_CREATED_AT = "createdAt";

    /** Bounded so a collision storm cannot spin forever; 10^6 space makes this ample. */
    private static final int MAX_ISSUE_ATTEMPTS = 5;

    private final Firestore firestore;

    public FirestoreLinkCodeStore(Firestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public LinkCode issue(String accountId) {
        deleteByAccountId(accountId);
        for (int i = 0; i < MAX_ISSUE_ATTEMPTS; i++) {
            String code = InMemoryLinkCodeStore.generateCode();
            Instant now = Instant.now();
            Instant expiresAt = now.plus(InMemoryLinkCodeStore.TTL);
            DocumentReference ref = firestore.collection(LINK_CODES).document(code);
            try {
                Boolean created = firestore.runTransaction(tx -> {
                    DocumentSnapshot existing = tx.get(ref).get();
                    // A live code for someone else must never be handed out twice.
                    if (existing.exists() && !isExpired(existing, now)) {
                        return false;
                    }
                    Map<String, Object> doc = new HashMap<>();
                    doc.put("code", code);
                    doc.put(FIELD_ACCOUNT_ID, accountId);
                    doc.put(FIELD_CREATED_AT, Timestamp.ofTimeSecondsAndNanos(now.getEpochSecond(), now.getNano()));
                    doc.put(FIELD_EXPIRES_AT,
                            Timestamp.ofTimeSecondsAndNanos(expiresAt.getEpochSecond(), expiresAt.getNano()));
                    doc.put(FIELD_ATTEMPTS, 0L);
                    doc.put(FIELD_REDEEMED, false);
                    tx.set(ref, doc);
                    return true;
                }).get();
                if (Boolean.TRUE.equals(created)) {
                    return new LinkCode(code, accountId, now, expiresAt, 0, false);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Firestore link code issue interrupted", ie);
            } catch (ExecutionException e) {
                throw new IllegalStateException("Firestore link code issue failed", e);
            }
        }
        throw new IllegalStateException("Could not allocate a free link code");
    }

    @Override
    public Optional<LinkCode> find(String code) {
        try {
            DocumentSnapshot snap = firestore.collection(LINK_CODES).document(code).get().get();
            return snap.exists() ? Optional.of(toLinkCode(snap)) : Optional.empty();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Firestore link code read interrupted", ie);
        } catch (ExecutionException e) {
            log.warn("Firestore link code read failed", e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<LinkCode> recordFailedAttempt(String code) {
        DocumentReference ref = firestore.collection(LINK_CODES).document(code);
        try {
            // Transactional so concurrent guesses cannot both read the same count and
            // write back the same increment, which would double the attempts allowed.
            return Optional.ofNullable(firestore.runTransaction(tx -> {
                DocumentSnapshot snap = tx.get(ref).get();
                if (!snap.exists()) {
                    return null;
                }
                long attempts = value(snap.get(FIELD_ATTEMPTS)) + 1;
                tx.update(ref, FIELD_ATTEMPTS, attempts);
                return new LinkCode(code, snap.getString(FIELD_ACCOUNT_ID),
                        instant(snap.get(FIELD_CREATED_AT)), instant(snap.get(FIELD_EXPIRES_AT)),
                        (int) attempts, Boolean.TRUE.equals(snap.getBoolean(FIELD_REDEEMED)));
            }).get());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Firestore link code attempt interrupted", ie);
        } catch (ExecutionException e) {
            log.warn("Firestore link code attempt update failed", e);
            return Optional.empty();
        }
    }

    @Override
    public boolean markRedeemed(String code) {
        DocumentReference ref = firestore.collection(LINK_CODES).document(code);
        try {
            // The single-use guarantee: two devices racing the same code both read
            // redeemed=false outside a transaction and both proceed.
            return Boolean.TRUE.equals(firestore.runTransaction(tx -> {
                DocumentSnapshot snap = tx.get(ref).get();
                if (!snap.exists() || Boolean.TRUE.equals(snap.getBoolean(FIELD_REDEEMED))) {
                    return false;
                }
                tx.update(ref, FIELD_REDEEMED, true);
                return true;
            }).get());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Firestore link code redeem interrupted", ie);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Firestore link code redeem failed", e);
        }
    }

    @Override
    public void deleteByAccountId(String accountId) {
        try {
            List<QueryDocumentSnapshot> mine = firestore.collection(LINK_CODES)
                    .whereEqualTo(FIELD_ACCOUNT_ID, accountId).get().get().getDocuments();
            for (QueryDocumentSnapshot snap : mine) {
                snap.getReference().delete().get();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Firestore link code delete interrupted", ie);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Firestore link code delete failed", e);
        }
    }

    private static boolean isExpired(DocumentSnapshot snap, Instant now) {
        Instant expiresAt = instant(snap.get(FIELD_EXPIRES_AT));
        return expiresAt == null || !expiresAt.isAfter(now);
    }

    private static LinkCode toLinkCode(DocumentSnapshot snap) {
        return new LinkCode(
                snap.getId(),
                snap.getString(FIELD_ACCOUNT_ID),
                instant(snap.get(FIELD_CREATED_AT)),
                instant(snap.get(FIELD_EXPIRES_AT)),
                (int) value(snap.get(FIELD_ATTEMPTS)),
                Boolean.TRUE.equals(snap.getBoolean(FIELD_REDEEMED)));
    }

    private static long value(Object raw) {
        return raw instanceof Number n ? n.longValue() : 0L;
    }

    private static Instant instant(Object raw) {
        return raw instanceof Timestamp ts ? ts.toDate().toInstant() : null;
    }
}
