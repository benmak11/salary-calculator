package app.salary.api.store;

import app.salary.common.dto.CalculateRequest;
import app.salary.common.dto.CalculateResponse;
import app.salary.common.dto.CalculationListResponse;
import app.salary.common.dto.SavedCalculationDetail;
import app.salary.common.dto.SavedCalculationSummary;

import java.time.Instant;
import java.util.Optional;

/**
 * Persistence for calculation history. One implementation per environment —
 * {@link FirestoreCalculationStore} in prod, {@link InMemoryCalculationStore} in tests
 * and when {@code ENABLE_GCP=false}.
 */
public interface CalculationStore {

    /**
     * Persists the calculation under a freshly generated document id and returns the
     * derived row data.
     */
    SavedCalculationSummary save(String userId, CalculateRequest request, CalculateResponse response);

    /**
     * Saves at a caller-supplied id and timestamp.
     *
     * <p>Exists so the B-1b dual-write mirror can reuse the id and {@code createdAt} the
     * authoritative side generated. Writing the mirror through {@link #save} instead would
     * mint a second, different id for the same calculation, and the migration's whole
     * premise is that document ids survive the re-key so a stored {@code calculationId}
     * stays valid.
     *
     * <p>Idempotent: writing the same id twice replaces rather than duplicates.
     */
    SavedCalculationSummary saveAt(String userId, String calcId, Instant savedAt,
                                   CalculateRequest request, CalculateResponse response);

    /**
     * Newest-first list of saved sessions for the user. {@code cursor} is reserved
     * for future paging; v1 always returns {@code nextCursor == null}.
     */
    CalculationListResponse list(String userId, int limit, String cursor);

    Optional<SavedCalculationDetail> get(String userId, String calcId);

    /** Returns true when the document existed and was removed, false when it didn't exist. */
    boolean delete(String userId, String calcId);

    /** Removes every saved calculation for the user. Returns the number of docs deleted. */
    int deleteAll(String userId);
}
