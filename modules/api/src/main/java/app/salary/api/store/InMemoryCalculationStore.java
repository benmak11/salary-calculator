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
        String calcId = UUID.randomUUID().toString();
        Instant savedAt = clock.instant();
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
        List<SavedCalculationSummary> items = new ArrayList<>();
        entries.values().stream()
                .sorted(Comparator.comparing((Entry e) -> e.savedAt).reversed())
                .limit(Math.max(1, limit))
                .forEach(e -> items.add(e.summary));
        return new CalculationListResponse(items, null);
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
