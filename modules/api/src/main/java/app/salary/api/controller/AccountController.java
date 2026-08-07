package app.salary.api.controller;

import app.salary.api.auth.AuthMiddleware;
import app.salary.api.store.AccountDirectory;
import app.salary.api.store.BudgetStore;
import app.salary.api.store.CalculationStore;
import app.salary.api.store.EntitlementStore;
import app.salary.api.store.LinkCodeStore;
import app.salary.api.store.GrantStore;
import app.salary.api.store.UserDirectory;
import app.salary.common.constants.ApiConstants;
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
    private final GrantStore grantStore;
    private final BudgetStore budgetStore;
    private final UserDirectory users;
    private final AccountDirectory accounts;
    private final EntitlementStore entitlements;
    private final LinkCodeStore linkCodes;

    public AccountController(CalculationStore calculationStore, GrantStore grantStore,
                              BudgetStore budgetStore, UserDirectory users,
                              AccountDirectory accounts, EntitlementStore entitlements,
                              LinkCodeStore linkCodes) {
        this.calculationStore = calculationStore;
        this.grantStore = grantStore;
        this.budgetStore = budgetStore;
        this.users = users;
        this.accounts = accounts;
        this.entitlements = entitlements;
        this.linkCodes = linkCodes;
    }

    public void register(RoutesConfig routes) {
        routes.delete("/v1/account", this::deleteAccount);
    }

    /**
     * Deletes the user's saved calculations, RSU grants, budget, directory record, and
     * account/identity mapping. Idempotent.
     *
     * <p>The mapping is purged even though nothing reads it yet: leaving it behind would
     * accumulate identity records pointing at deleted users, and the migration would later
     * resurrect accounts for people who asked to be forgotten.
     *
     * <p>Entitlements and any outstanding link codes go with it, for the same reason and one
     * more: a live link code outliving its account would let a stranger redeem their way onto
     * a deleted account's id. Resolution happens <em>before</em> the identity records are
     * removed, because afterwards there is no way back from the sub to the accountId.
     */
    private void deleteAccount(Context ctx) {
        Optional<String> userId = AuthMiddleware.currentUserId(ctx);
        if (userId.isEmpty()) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of(ApiConstants.ERROR, "Authentication required"));
            return;
        }
        MDC.put(ApiConstants.MDC_USER_ID, userId.get());
        int removed = calculationStore.deleteAll(userId.get());
        int grantsRemoved = grantStore.deleteAll(userId.get());
        boolean budgetRemoved = budgetStore.delete(userId.get());
        users.delete(userId.get());

        // Resolve first: deleteByProviderSub removes the identity that makes this lookup work.
        Optional<String> accountId = accounts == null
                ? Optional.empty()
                : accounts.findAccountIdBySub(userId.get());
        accountId.ifPresent(id -> {
            if (entitlements != null) entitlements.deleteAll(id);
            if (linkCodes != null) linkCodes.deleteByAccountId(id);
        });

        int identitiesRemoved = accounts != null ? accounts.deleteByProviderSub(userId.get()) : 0;
        log.info("account deleted: calculationsRemoved={} grantsRemoved={} budgetRemoved={} "
                        + "identitiesRemoved={} entitlementsPurged={}",
                removed, grantsRemoved, budgetRemoved, identitiesRemoved, accountId.isPresent());
        ctx.status(HttpStatus.NO_CONTENT);
    }
}
