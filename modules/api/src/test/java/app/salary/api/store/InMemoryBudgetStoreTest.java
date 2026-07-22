package app.salary.api.store;

import app.salary.common.constants.BudgetBucket;
import app.salary.common.constants.ExpenseCadence;
import app.salary.common.constants.GoalType;
import app.salary.common.dto.Budget;
import app.salary.common.dto.Expense;
import app.salary.common.dto.SavingsGoal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryBudgetStoreTest {

    private InMemoryBudgetStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryBudgetStore();
    }

    private static Budget budget() {
        SavingsGoal goal = new SavingsGoal();
        goal.setId("sg_1");
        goal.setType(GoalType.EMERGENCY_FUND);
        goal.setName("Emergency fund");
        goal.setTargetAmount(10000.0);
        goal.setPriority(1);

        Expense expense = new Expense();
        expense.setId("exp_1");
        expense.setName("Rent");
        expense.setAmount(1800.0);
        expense.setCadence(ExpenseCadence.MONTHLY);
        expense.setBucket(BudgetBucket.NEEDS);

        Budget budget = new Budget();
        budget.setGoals(List.of(goal));
        budget.setExpenses(List.of(expense));
        return budget;
    }

    @Test
    void get_unknownUser_returnsEmpty() {
        assertTrue(store.get("nobody").isEmpty());
    }

    @Test
    void save_thenGet_returnsTheSavedBudget() {
        Budget saved = store.save("user-1", budget());

        Optional<Budget> loaded = store.get("user-1");
        assertTrue(loaded.isPresent());
        assertEquals(1, loaded.get().getGoals().size());
        assertEquals("Emergency fund", loaded.get().getGoals().get(0).getName());
        assertEquals(1, loaded.get().getExpenses().size());
        assertEquals("Rent", loaded.get().getExpenses().get(0).getName());
        assertSame(saved, loaded.get());
    }

    @Test
    void save_replacesThePreviousBudgetWholesale() {
        store.save("user-1", budget());

        Budget replacement = new Budget();
        replacement.setGoals(List.of());
        replacement.setExpenses(List.of());
        store.save("user-1", replacement);

        Optional<Budget> loaded = store.get("user-1");
        assertTrue(loaded.isPresent());
        assertTrue(loaded.get().getGoals().isEmpty());
        assertTrue(loaded.get().getExpenses().isEmpty());
    }

    @Test
    void save_isScopedPerUser() {
        store.save("user-1", budget());

        assertTrue(store.get("user-2").isEmpty());
    }

    @Test
    void delete_existingBudget_removesIt() {
        store.save("user-1", budget());

        assertTrue(store.delete("user-1"));
        assertTrue(store.get("user-1").isEmpty());
        assertFalse(store.delete("user-1"));
    }

    @Test
    void delete_unknownUser_returnsFalse() {
        assertFalse(store.delete("nobody"));
    }
}
