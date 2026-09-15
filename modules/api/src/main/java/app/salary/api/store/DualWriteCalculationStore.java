package app.salary.api.store;

import app.salary.common.dto.CalculateRequest;
import app.salary.common.dto.CalculateResponse;
import app.salary.common.dto.CalculationListResponse;
import app.salary.common.dto.SavedCalculationDetail;
import app.salary.common.dto.SavedCalculationSummary;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * B-1b Phase 1 decorator for {@link CalculationStore}. See {@link DualWriteStore}.
 *
 * <p>The mirror reuses both the id and the {@code savedAt} the authoritative side produced,
 * via {@link CalculationStore#saveAt}. The timestamp matters as much as the id: the history
 * endpoint orders on {@code createdAt}, so two independently generated timestamps would give
 * the layouts different orderings for identical data — a divergence the Phase 2 parity check
 * is least likely to notice, because the row counts would still match.
 */
public class DualWriteCalculationStore extends DualWriteStore<CalculationStore>
        implements CalculationStore {

    public DualWriteCalculationStore(CalculationStore subKeyed, CalculationStore accountKeyed,
                                     AccountIdResolver resolver, boolean readAccountKeyed) {
        super(subKeyed, accountKeyed, resolver, readAccountKeyed);
    }

    @Override
    public SavedCalculationSummary save(String userId, CalculateRequest request, CalculateResponse response) {
        Sides<CalculationStore> s = sides(userId);
        SavedCalculationSummary saved = s.authoritative().save(s.key(), request, response);
        s.alsoWrite("calculation.save", (store, key) ->
                store.saveAt(key, saved.getId(), savedAtOf(saved), request, response));
        return saved;
    }

    @Override
    public SavedCalculationSummary saveAt(String userId, String calcId, Instant savedAt,
                                          CalculateRequest request, CalculateResponse response) {
        Sides<CalculationStore> s = sides(userId);
        SavedCalculationSummary saved = s.authoritative().saveAt(s.key(), calcId, savedAt, request, response);
        s.alsoWrite("calculation.saveAt", (store, key) ->
                store.saveAt(key, calcId, savedAt, request, response));
        return saved;
    }

    /**
     * A summary carries {@code savedAt} as an ISO-8601 string. If it ever stops parsing this
     * throws, which {@code alsoWrite} turns into a skipped mirror rather than a failed save:
     * a missing row is something the parity check reports, whereas a row that silently
     * disagrees on ordering is the divergence it is least likely to catch.
     */
    private static Instant savedAtOf(SavedCalculationSummary saved) {
        try {
            return Instant.parse(saved.getSavedAt());
        } catch (DateTimeParseException | NullPointerException e) {
            throw new IllegalStateException("unparseable savedAt on a saved summary", e);
        }
    }

    @Override
    public CalculationListResponse list(String userId, int limit, String cursor) {
        Sides<CalculationStore> s = sides(userId);
        return s.authoritative().list(s.key(), limit, cursor);
    }

    @Override
    public Optional<SavedCalculationDetail> get(String userId, String calcId) {
        Sides<CalculationStore> s = sides(userId);
        return s.authoritative().get(s.key(), calcId);
    }

    @Override
    public boolean delete(String userId, String calcId) {
        Sides<CalculationStore> s = sides(userId);
        boolean deleted = s.authoritative().delete(s.key(), calcId);
        s.alsoWrite("calculation.delete", (store, key) -> store.delete(key, calcId));
        return deleted;
    }

    @Override
    public int deleteAll(String userId) {
        Sides<CalculationStore> s = sides(userId);
        int removed = s.authoritative().deleteAll(s.key());
        s.alsoWrite("calculation.deleteAll", CalculationStore::deleteAll);
        return removed;
    }
}
