package app.salary.api.store;

import app.salary.common.dto.CalculateRequest;
import app.salary.common.dto.CalculateResponse;
import app.salary.common.dto.CalculationListResponse;
import app.salary.common.dto.SavedCalculationDetail;
import app.salary.common.dto.SavedCalculationSummary;

import java.util.Optional;

/**
 * Persistence for calculation history. One implementation per environment —
 * {@link FirestoreCalculationStore} in prod, {@link InMemoryCalculationStore} in tests
 * and when {@code ENABLE_GCP=false}.
 */
public interface CalculationStore {

    /** Persists the calculation under the user's history and returns the derived row data. */
    SavedCalculationSummary save(String userId, CalculateRequest request, CalculateResponse response);

    /**
     * Newest-first list of saved sessions for the user. {@code cursor} is reserved
     * for future paging; v1 always returns {@code nextCursor == null}.
     */
    CalculationListResponse list(String userId, int limit, String cursor);

    Optional<SavedCalculationDetail> get(String userId, String calcId);

    /** Returns true when the document existed and was removed, false when it didn't exist. */
    boolean delete(String userId, String calcId);
}
