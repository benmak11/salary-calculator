package app.salary.api.store;

import app.salary.common.dto.PaycheckCheckIn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link CheckInStore} for local runs and tests.
 *
 * <p>Keyed accountId to payDate to entry, which is what makes the one-per-payday rule
 * structural here rather than enforced by a query — the same shape the Firestore impl gets
 * from using the pay date as its document id.
 */
public class InMemoryCheckInStore implements CheckInStore {

    private final Map<String, Map<String, PaycheckCheckIn>> byAccount = new ConcurrentHashMap<>();

    @Override
    public PaycheckCheckIn save(String accountId, PaycheckCheckIn checkIn) {
        Map<String, PaycheckCheckIn> mine = byAccount.computeIfAbsent(accountId, k -> new ConcurrentHashMap<>());
        // Replacing on the same pay date rather than appending: a correction must not
        // double-count in the YTD totals built on top of this.
        PaycheckCheckIn existing = mine.get(checkIn.getPayDate());
        checkIn.setId(existing != null ? existing.getId() : Ulid.generate());
        mine.put(checkIn.getPayDate(), checkIn);
        return checkIn;
    }

    @Override
    public List<PaycheckCheckIn> list(String accountId, int limit) {
        Map<String, PaycheckCheckIn> mine = byAccount.get(accountId);
        if (mine == null) {
            return List.of();
        }
        List<PaycheckCheckIn> all = new ArrayList<>(mine.values());
        all.sort(Comparator.comparing(PaycheckCheckIn::getPayDate).reversed());
        return all.size() <= limit ? all : new ArrayList<>(all.subList(0, limit));
    }

    @Override
    public Optional<PaycheckCheckIn> find(String accountId, String id) {
        Map<String, PaycheckCheckIn> mine = byAccount.get(accountId);
        if (mine == null) {
            return Optional.empty();
        }
        return mine.values().stream().filter(c -> id.equals(c.getId())).findFirst();
    }

    @Override
    public boolean delete(String accountId, String id) {
        Map<String, PaycheckCheckIn> mine = byAccount.get(accountId);
        if (mine == null) {
            return false;
        }
        return mine.entrySet().removeIf(e -> id.equals(e.getValue().getId()));
    }

    @Override
    public int deleteAll(String accountId) {
        Map<String, PaycheckCheckIn> removed = byAccount.remove(accountId);
        return removed == null ? 0 : removed.size();
    }
}
