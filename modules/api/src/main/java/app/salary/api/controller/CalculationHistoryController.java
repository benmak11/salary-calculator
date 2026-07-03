package app.salary.api.controller;

import app.salary.api.auth.AuthMiddleware;
import app.salary.api.store.CalculationStore;
import app.salary.common.constants.ApiConstants;
import app.salary.common.dto.CalculationListResponse;
import app.salary.common.dto.SavedCalculationDetail;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Map;
import java.util.Optional;

/** History endpoints for signed-in users. Anonymous requests get 401. */
public class CalculationHistoryController {
    private static final Logger log = LoggerFactory.getLogger(CalculationHistoryController.class);
    private static final int DEFAULT_LIMIT = 20;

    private final CalculationStore store;

    public CalculationHistoryController(CalculationStore store) {
        this.store = store;
    }

    public void register(RoutesConfig routes) {
        routes.get("/v1/calculations", this::list);
        routes.get("/v1/calculations/{id}", this::get);
        routes.delete("/v1/calculations/{id}", this::delete);
    }

    private void list(Context ctx) {
        Optional<String> userId = AuthMiddleware.currentUserId(ctx);
        if (userId.isEmpty()) {
            unauthorized(ctx);
            return;
        }
        MDC.put(ApiConstants.MDC_USER_ID, userId.get());
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(DEFAULT_LIMIT);
        String cursor = ctx.queryParam("cursor");
        CalculationListResponse response = store.list(userId.get(), limit, cursor);
        log.info("history list: count={}", response.getItems() == null ? 0 : response.getItems().size());
        ctx.json(response);
    }

    private void get(Context ctx) {
        Optional<String> userId = AuthMiddleware.currentUserId(ctx);
        if (userId.isEmpty()) {
            unauthorized(ctx);
            return;
        }
        MDC.put(ApiConstants.MDC_USER_ID, userId.get());
        String calcId = ctx.pathParam("id");
        Optional<SavedCalculationDetail> detail = store.get(userId.get(), calcId);
        if (detail.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of(ApiConstants.ERROR, "Not found"));
            return;
        }
        ctx.json(detail.get());
    }

    private void delete(Context ctx) {
        Optional<String> userId = AuthMiddleware.currentUserId(ctx);
        if (userId.isEmpty()) {
            unauthorized(ctx);
            return;
        }
        MDC.put(ApiConstants.MDC_USER_ID, userId.get());
        String calcId = ctx.pathParam("id");
        boolean removed = store.delete(userId.get(), calcId);
        if (!removed) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of(ApiConstants.ERROR, "Not found"));
            return;
        }
        log.info("history delete: id={}", calcId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void unauthorized(Context ctx) {
        ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of(ApiConstants.ERROR, "Authentication required"));
    }
}
