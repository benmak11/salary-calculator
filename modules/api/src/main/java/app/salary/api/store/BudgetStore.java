package app.salary.api.store;

import app.salary.common.dto.Budget;

import java.util.Optional;

/**
 * Persistence for a user's single household budget. Unlike {@link GrantStore}
 * (a list of independently addressable items), a budget is one object per
 * user — get/save/delete, no per-item id routing.
 */
public interface BudgetStore {

    /** The user's saved budget, if any. */
    Optional<Budget> get(String userId);

    /** Replaces the user's budget wholesale. Returns the stored budget. */
    Budget save(String userId, Budget budget);

    /** Returns true when a budget existed for the user and was removed. */
    boolean delete(String userId);
}
