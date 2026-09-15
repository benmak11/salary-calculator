package app.salary.api.store;

import app.salary.common.dto.Budget;

import java.util.Optional;

/**
 * B-1b Phase 1 decorator for {@link BudgetStore}. See {@link DualWriteStore} for how the
 * two layouts are resolved and {@code ops/B-1b-migration-rollback-plan.md} for the phases.
 *
 * <p>Simplest of the three: a budget is one document per user with a fixed id, so there is
 * no id to keep in step across the layouts.
 */
public class DualWriteBudgetStore extends DualWriteStore<BudgetStore> implements BudgetStore {

    public DualWriteBudgetStore(BudgetStore subKeyed, BudgetStore accountKeyed,
                                AccountIdResolver resolver, boolean readAccountKeyed) {
        super(subKeyed, accountKeyed, resolver, readAccountKeyed);
    }

    @Override
    public Optional<Budget> get(String userId) {
        Sides<BudgetStore> s = sides(userId);
        return s.authoritative().get(s.key());
    }

    @Override
    public Budget save(String userId, Budget budget) {
        Sides<BudgetStore> s = sides(userId);
        Budget saved = s.authoritative().save(s.key(), budget);
        s.alsoWrite("budget.save", (store, key) -> store.save(key, budget));
        return saved;
    }

    @Override
    public boolean delete(String userId) {
        Sides<BudgetStore> s = sides(userId);
        boolean deleted = s.authoritative().delete(s.key());
        s.alsoWrite("budget.delete", BudgetStore::delete);
        return deleted;
    }
}
