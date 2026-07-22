package app.salary.api.store;

import app.salary.common.dto.Budget;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Map-backed {@link BudgetStore} for tests and bootless dev environments.
 */
public class InMemoryBudgetStore implements BudgetStore {
    private final Map<String, Budget> byUser = new ConcurrentHashMap<>();

    @Override
    public Optional<Budget> get(String userId) {
        return Optional.ofNullable(byUser.get(userId));
    }

    @Override
    public Budget save(String userId, Budget budget) {
        byUser.put(userId, budget);
        return budget;
    }

    @Override
    public boolean delete(String userId) {
        return byUser.remove(userId) != null;
    }
}
