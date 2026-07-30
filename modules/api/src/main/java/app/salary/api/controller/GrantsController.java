package app.salary.api.controller;

import app.salary.api.auth.AuthMiddleware;
import app.salary.api.store.GrantStore;
import app.salary.api.validation.RequestValidator;
import app.salary.common.constants.ApiConstants;
import app.salary.common.dto.GrantListResponse;
import app.salary.common.dto.RsuGrant;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

/**
 * RSU grant sync for signed-in users. Anonymous requests get 401 — signed-out
 * users keep grants on-device only. Amount-bearing fields are never logged.
 */
public class GrantsController {
    private static final Logger log = LoggerFactory.getLogger(GrantsController.class);

    private final GrantStore store;
    private final RequestValidator validator;

    public GrantsController(GrantStore store, RequestValidator validator) {
        this.store = store;
        this.validator = validator;
    }

    public void register(RoutesConfig routes) {
        routes.get("/v1/grants", this::list);
        routes.post("/v1/grants", this::create);
        routes.put("/v1/grants/{id}", this::update);
        routes.delete("/v1/grants/{id}", this::delete);
    }

    private void list(Context ctx) {
        Optional<String> userId = AuthMiddleware.requireUser(ctx);
        if (userId.isEmpty()) return;
        GrantListResponse response = new GrantListResponse(store.list(userId.get()));
        log.info("grants list: count={}", response.getItems().size());
        ctx.json(response);
    }

    private void create(Context ctx) {
        Optional<String> userId = AuthMiddleware.requireUser(ctx);
        if (userId.isEmpty()) return;
        RsuGrant grant = ctx.bodyAsClass(RsuGrant.class);
        validator.validate(grant);
        RsuGrant saved = store.create(userId.get(), grant);
        log.info("grants create: id={}", saved.getId());
        ctx.status(HttpStatus.CREATED).json(saved);
    }

    private void update(Context ctx) {
        Optional<String> userId = AuthMiddleware.requireUser(ctx);
        if (userId.isEmpty()) return;
        String grantId = ctx.pathParam("id");
        RsuGrant grant = ctx.bodyAsClass(RsuGrant.class);
        validator.validate(grant);
        Optional<RsuGrant> updated = store.update(userId.get(), grantId, grant);
        if (updated.isEmpty()) {
            notFound(ctx);
            return;
        }
        log.info("grants update: id={}", grantId);
        ctx.json(updated.get());
    }

    private void delete(Context ctx) {
        Optional<String> userId = AuthMiddleware.requireUser(ctx);
        if (userId.isEmpty()) return;
        String grantId = ctx.pathParam("id");
        if (!store.delete(userId.get(), grantId)) {
            notFound(ctx);
            return;
        }
        log.info("grants delete: id={}", grantId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void notFound(Context ctx) {
        ctx.status(HttpStatus.NOT_FOUND).json(Map.of(ApiConstants.ERROR, "Not found"));
    }
}
