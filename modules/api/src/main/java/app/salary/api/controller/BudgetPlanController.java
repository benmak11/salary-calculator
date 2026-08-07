package app.salary.api.controller;

import app.salary.api.auth.AuthMiddleware;
import app.salary.api.client.GenerativeAiException;
import app.salary.api.service.BudgetPlanService;
import app.salary.api.service.EntitlementService;
import app.salary.api.service.SubscriptionRequiredException;
import app.salary.api.store.AccountDirectory;
import app.salary.api.validation.RequestValidator;
import app.salary.common.constants.ApiConstants;
import app.salary.common.dto.BudgetPlan;
import app.salary.common.dto.BudgetPlanRequest;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * AI-suggested budget strategy for a goals/expenses combo the client already
 * has locally. Anonymous requests get 401.
 *
 * <p><b>Auth is an abuse control, not an access-model decision.</b> Every call
 * invokes Vertex AI, which bills per request, so leaving this open was an
 * unauthenticated path to a metered paid API. Same reasoning already applied to
 * {@code /v1/stocks/*}, which is gated to keep the Finnhub key from being
 * farmed. The budget being planned still does not have to be saved first — the
 * request carries it — so this remains usable before any budget is synced.
 *
 * <p>No client impact when this changed: the iOS Budget CTA is already gated on
 * being signed in ({@code InsightsTab.swift}, {@code showBudgetCTA: isSignedIn})
 * and Android has no budget feature yet, so no reachable caller was anonymous.
 *
 * <p>When Vertex AI is unavailable (no GCP creds, or the model call fails) this
 * answers 503 and the client falls back to BudgetEngine's own deterministic
 * contribution rates.
 *
 * <p><b>This is the first Pro-gated route, and the gate ships off.</b> With
 * {@code SUBSCRIPTION_ENFORCEMENT=false} the entitlement check runs and logs what it would
 * have decided, then admits everyone. Grandfathered accounts
 * ({@code legacy_pro_budget}, roadmap §8.5) keep access permanently even once it is on, so
 * enforcement must not be enabled until the B1 migration has backfilled that flag.
 */
public class BudgetPlanController {
    private static final Logger log = LoggerFactory.getLogger(BudgetPlanController.class);

    private final BudgetPlanService service;
    private final RequestValidator validator;
    private final EntitlementService entitlements;
    private final AccountDirectory accounts;

    /** {@code service} may be null when Vertex AI isn't configured. */
    public BudgetPlanController(BudgetPlanService service, RequestValidator validator,
                                EntitlementService entitlements, AccountDirectory accounts) {
        this.service = service;
        this.validator = validator;
        this.entitlements = entitlements;
        this.accounts = accounts;
    }

    public void register(RoutesConfig routes) {
        routes.post("/v1/budget/plan", this::generate);
    }

    private void generate(Context ctx) {
        // Auth is checked before the service-null branch: an anonymous caller must not be
        // able to distinguish "Vertex AI is off" from "you are not signed in", and the
        // 401 costs nothing to produce.
        Optional<String> userId = AuthMiddleware.requireUser(ctx);
        if (userId.isEmpty()) return;

        // Ordered after auth but before the service-null branch, so a caller who is refused
        // for lack of a subscription gets the same answer whether or not Vertex AI is up.
        requireEntitlement(userId.get());

        if (service == null) {
            unavailable(ctx);
            return;
        }

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

    /**
     * Resolves the caller's accountId and checks the gate. A caller with no accountId (they
     * last signed in before the identity schema shipped) resolves to null and is treated as
     * unentitled, which while enforcement is off still admits them.
     */
    private void requireEntitlement(String userId) {
        if (entitlements == null) {
            return;
        }
        Optional<String> accountId = accounts == null
                ? Optional.empty()
                : accounts.findAccountIdBySub(userId);
        if (!entitlements.allows(accountId, EntitlementService.FEATURE_BUDGET_PLAN, Instant.now())) {
            throw new SubscriptionRequiredException(EntitlementService.FEATURE_BUDGET_PLAN);
        }
    }

    private void unavailable(Context ctx) {
        ctx.status(HttpStatus.SERVICE_UNAVAILABLE).json(Map.of(ApiConstants.ERROR, "Budget plan generation unavailable"));
    }
}
