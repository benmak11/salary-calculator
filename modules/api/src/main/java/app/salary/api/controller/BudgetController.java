package app.salary.api.controller;

import app.salary.api.auth.AuthMiddleware;
import app.salary.api.store.BudgetStore;
import app.salary.api.validation.RequestValidator;
import app.salary.common.constants.ApiConstants;
import app.salary.common.dto.Budget;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

/**
 * Single household budget (savings goals + itemized expenses) for signed-in
 * users. Anonymous requests get 401 — signed-out users keep budgets on-device
 * only. Amount-bearing fields are never logged.
 */
public class BudgetController {
    private static final Logger log = LoggerFactory.getLogger(BudgetController.class);
    private static final String BUDGET_PATH = "/v1/budget";

    private final BudgetStore store;
    private final RequestValidator validator;

    public BudgetController(BudgetStore store, RequestValidator validator) {
        this.store = store;
        this.validator = validator;
    }

    public void register(RoutesConfig routes) {
        routes.get(BUDGET_PATH, this::get);
        routes.put(BUDGET_PATH, this::put);
        routes.delete(BUDGET_PATH, this::delete);
    }

    private void get(Context ctx) {
        Optional<String> userId = AuthMiddleware.requireUser(ctx);
        if (userId.isEmpty()) return;
        Optional<Budget> budget = store.get(userId.get());
        if (budget.isEmpty()) {
            notFound(ctx);
            return;
        }
        ctx.json(budget.get());
    }

    private void put(Context ctx) {
        Optional<String> userId = AuthMiddleware.requireUser(ctx);
        if (userId.isEmpty()) return;
        Budget budget = ctx.bodyAsClass(Budget.class);
        validator.validate(budget);
        Budget saved = store.save(userId.get(), budget);
        log.info("budget save: goals={} expenses={}", saved.getGoals().size(), saved.getExpenses().size());
        ctx.json(saved);
    }

    private void delete(Context ctx) {
        Optional<String> userId = AuthMiddleware.requireUser(ctx);
        if (userId.isEmpty()) return;
        if (!store.delete(userId.get())) {
            notFound(ctx);
            return;
        }
        log.info("budget delete");
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void notFound(Context ctx) {
        ctx.status(HttpStatus.NOT_FOUND).json(Map.of(ApiConstants.ERROR, "Not found"));
    }
}
