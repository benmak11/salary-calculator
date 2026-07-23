package app.salary.api.controller;

import app.salary.api.auth.AuthMiddleware;
import app.salary.api.client.GenerativeAiException;
import app.salary.api.service.BudgetPlanService;
import app.salary.api.validation.RequestValidator;
import app.salary.common.constants.ApiConstants;
import app.salary.common.dto.BudgetPlan;
import app.salary.common.dto.BudgetPlanRequest;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Map;
import java.util.Optional;

/**
 * AI-suggested budget strategy for a goals/expenses combo the client already
 * has locally. Auth optional, same as {@code /v1/calculate} — the budget
 * being planned doesn't have to be saved yet, so anonymous users can preview
 * a plan too. When Vertex AI is unavailable (no GCP creds, or the model call
 * fails) this answers 503 and the client falls back to BudgetEngine's own
 * deterministic contribution rates.
 */
public class BudgetPlanController {
    private static final Logger log = LoggerFactory.getLogger(BudgetPlanController.class);

    private final BudgetPlanService service;
    private final RequestValidator validator;

    /** {@code service} may be null when Vertex AI isn't configured. */
    public BudgetPlanController(BudgetPlanService service, RequestValidator validator) {
        this.service = service;
        this.validator = validator;
    }

    public void register(RoutesConfig routes) {
        routes.post("/v1/budget/plan", this::generate);
    }

    private void generate(Context ctx) {
        if (service == null) {
            unavailable(ctx);
            return;
        }
        Optional<String> userId = AuthMiddleware.currentUserId(ctx);
        userId.ifPresent(id -> MDC.put(ApiConstants.MDC_USER_ID, id));

        BudgetPlanRequest request = ctx.bodyAsClass(BudgetPlanRequest.class);
        validator.validate(request);

        try {
            BudgetPlan plan = service.generatePlan(request);
            log.info("budget plan generated: goals={} contributions={} warnings={}",
                    request.getBudget().getGoals().size(), plan.getGoalContributions().size(), plan.getWarnings().size());
            ctx.json(plan);
        } catch (GenerativeAiException e) {
            log.warn("budget plan generation failed: {}", e.getMessage());
            unavailable(ctx);
        }
    }

    private void unavailable(Context ctx) {
        ctx.status(HttpStatus.SERVICE_UNAVAILABLE).json(Map.of(ApiConstants.ERROR, "Budget plan generation unavailable"));
    }
}
