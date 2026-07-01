package app.salary.api.controller;

import app.salary.api.auth.AuthMiddleware;
import app.salary.api.store.CalculationStore;
import app.salary.api.store.UserDirectory;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Map;
import java.util.Optional;

/**
 * Account-scoped endpoints for signed-in users. Currently just account deletion,
 * which App Store Guideline 5.1.1(v) requires for apps that create accounts.
 * Anonymous requests get 401.
 */
public class AccountController {
    private static final Logger log = LoggerFactory.getLogger(AccountController.class);

    private final CalculationStore calculationStore;
    private final UserDirectory users;

    public AccountController(CalculationStore calculationStore, UserDirectory users) {
        this.calculationStore = calculationStore;
        this.users = users;
    }

    public void register(RoutesConfig routes) {
        routes.delete("/v1/account", this::deleteAccount);
    }

    /** Deletes the user's saved calculations and their directory record. Idempotent. */
    private void deleteAccount(Context ctx) {
        Optional<String> userId = AuthMiddleware.currentUserId(ctx);
        if (userId.isEmpty()) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "Authentication required"));
            return;
        }
        MDC.put("user_id", userId.get());
        int removed = calculationStore.deleteAll(userId.get());
        users.delete(userId.get());
        log.info("account deleted: calculationsRemoved={}", removed);
        ctx.status(HttpStatus.NO_CONTENT);
    }
}
