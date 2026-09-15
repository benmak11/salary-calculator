package app.salary.api.store;

import app.salary.common.dto.Budget;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 * Firestore-backed {@link BudgetStore}.
 * <p>
 * Layout: a single fixed document at {@code users/{userId}/budget/current}
 * (one budget per user, unlike grants' auto-id-per-item collection).
 */
public class FirestoreBudgetStore implements BudgetStore {
    private static final TypeReference<Map<String, Object>> MAP_REF = new TypeReference<>() {};

    private final Firestore firestore;
    private final ObjectMapper mapper;

    private final StoreLayout layout;

    public FirestoreBudgetStore(Firestore firestore, ObjectMapper mapper) {
        this(firestore, mapper, StoreLayout.SUB_KEYED);
    }

    public FirestoreBudgetStore(Firestore firestore, ObjectMapper mapper, StoreLayout layout) {
        this.firestore = firestore;
        this.mapper = mapper;
        this.layout = layout;
    }

    @Override
    public Optional<Budget> get(String userId) {
        try {
            DocumentSnapshot snap = doc(userId).get().get();
            if (!snap.exists())
                return Optional.empty();
            return Optional.ofNullable(mapper.convertValue(snap.getData(), Budget.class));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Firestore budget get interrupted", ie);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Firestore budget get failed", e);
        }
    }

    @Override
    public Budget save(String userId, Budget budget) {
        try {
            doc(userId).set(mapper.convertValue(budget, MAP_REF)).get();
            return budget;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Firestore budget save interrupted", ie);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Firestore budget save failed", e);
        }
    }

    @Override
    public boolean delete(String userId) {
        DocumentReference ref = doc(userId);
        try {
            DocumentSnapshot snap = ref.get().get();
            if (!snap.exists())
                return false;
            ref.delete().get();
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Firestore budget delete interrupted", ie);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Firestore budget delete failed", e);
        }
    }

    private DocumentReference doc(String key) {
        return switch (layout) {
            case SUB_KEYED -> firestore.collection(StoreConstants.USERS).document(key)
                    .collection(StoreConstants.BUDGET).document(StoreConstants.BUDGET_DOC_ID);
            // A budget is one object per user, not a list, so it is a document rather than
            // a document with an entries subcollection like the other two.
            case ACCOUNT_KEYED -> firestore.collection(StoreConstants.BUDGETS).document(key);
        };
    }
}
