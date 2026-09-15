package app.salary.api.store;

import app.salary.common.dto.CalculateRequest;
import app.salary.common.dto.CalculateResponse;
import app.salary.common.dto.CalculationListResponse;
import app.salary.common.dto.SavedCalculationDetail;
import app.salary.common.dto.SavedCalculationSummary;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Map-backed {@link CalculationStore} for tests and bootless dev environments.
 * Thread-safe enough for those uses; not intended for production.
 */
public class InMemoryCalculationStore implements CalculationStore {
    private final Clock clock;
    private final Map<String, Map<String, Entry>> byUser = new ConcurrentHashMap<>();

    public InMemoryCalculationStore() { this(Clock.systemUTC()); }
    public InMemoryCalculationStore(Clock clock) { this.clock = clock; }

    @Override
    public SavedCalculationSummary save(String userId, CalculateRequest request, CalculateResponse response) {
        return saveAt(userId, UUID.randomUUID().toString(), clock.instant(), request, response);
    }

    @Override
    public SavedCalculationSummary saveAt(String userId, String calcId, Instant savedAt,
                                          CalculateRequest request, CalculateResponse response) {
        SavedCalculationSummary summary = CalculationSummarizer.summarize(calcId, savedAt, request, response);
        Entry entry = new Entry(summary, request, response, savedAt);
        byUser.computeIfAbsent(userId, k -> new HashMap<>()).put(calcId, entry);
        return summary;
    }

    @Override
    public CalculationListResponse list(String userId, int limit, String cursor) {
        Map<String, Entry> entries = byUser.get(userId);
        if (entries == null || entries.isEmpty()) {
            return new CalculationListResponse(List.of(), null);
        }
        int safeLimit = Math.clamp(limit, 1, 100);
        // Newest first, id as the tiebreak — the same total order the Firestore store pages
        // on, so a cursor round-trips identically against either implementation.
        List<Entry> ordered = entries.values().stream()
                .sorted(Comparator.comparing((Entry e) -> e.savedAt).reversed()
                        .thenComparing(e -> e.summary.getId(), Comparator.reverseOrder()))
                .toList();
        int start = 0;
        if (cursor != null && !cursor.isBlank()) {
            for (int i = 0; i < ordered.size(); i++) {
                if (ordered.get(i).summary.getId().equals(cursor)) {
                    start = i + 1;
                    break;
                }
            }
        }
        List<Entry> page = ordered.subList(start, Math.min(ordered.size(), start + safeLimit));
        List<SavedCalculationSummary> items = new ArrayList<>(page.size());
        page.forEach(e -> items.add(e.summary));
        String next = page.size() == safeLimit ? page.get(page.size() - 1).summary.getId() : null;
        return new CalculationListResponse(items, next);
    }

    @Override
    public Optional<SavedCalculationDetail> get(String userId, String calcId) {
        Map<String, Entry> entries = byUser.get(userId);
        if (entries == null) return Optional.empty();
        Entry entry = entries.get(calcId);
        if (entry == null) return Optional.empty();
        return Optional.of(new SavedCalculationDetail(entry.summary, entry.request, entry.response));
    }

    @Override
    public boolean delete(String userId, String calcId) {
        Map<String, Entry> entries = byUser.get(userId);
        if (entries == null) return false;
        return entries.remove(calcId) != null;
    }

    @Override
    public int deleteAll(String userId) {
        Map<String, Entry> entries = byUser.remove(userId);
        return entries == null ? 0 : entries.size();
    }

    private record Entry(SavedCalculationSummary summary,
                         CalculateRequest request,
                         CalculateResponse response,
                         Instant savedAt) {}
}
