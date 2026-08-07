package app.salary.api.controller;

import app.salary.api.auth.AuthMiddleware;
import app.salary.api.store.AccountDirectory;
import app.salary.api.store.CheckInStore;
import app.salary.api.validation.RequestValidator;
import app.salary.api.validation.ValidationException;
import app.salary.common.constants.ApiConstants;
import app.salary.common.dto.PaycheckCheckIn;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Paycheck check-in: one tap on payday to confirm what actually landed against what was
 * predicted. Auth-required, 401 when anonymous.
 *
 * <p><b>Free forever, never gated.</b> Check-in is the DAU engine — Pro sells the
 * interpretation of this data (YTD totals, contribution limits, drift alerts), not the act of
 * recording it. Putting a paywall on the recording would convert the growth loop into a
 * conversion event and kill both, so this deliberately carries no entitlement check even
 * though the routes land after B-3 shipped one.
 *
 * <p><b>Keyed on accountId, not provider sub.</b> That is why B-1a landed before any of this:
 * a collection created against a sub has to be migrated twice. A caller whose identity
 * predates that schema has no accountId yet and gets a 409 asking them to sign in again,
 * which mints one — the same treatment {@code AccountLinkController} gives the same case.
 *
 * <p>Never logs the amounts. They are realized income, which is a stricter category than the
 * projections the calculator handles.
 */
public class CheckInController {
    private static final Logger log = LoggerFactory.getLogger(CheckInController.class);

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final CheckInStore store;
    private final AccountDirectory accounts;
    private final RequestValidator validator;

    public CheckInController(CheckInStore store, AccountDirectory accounts, RequestValidator validator) {
        this.store = store;
        this.accounts = accounts;
        this.validator = validator;
    }

    public void register(RoutesConfig routes) {
        routes.post("/v1/checkins", this::create);
        routes.get("/v1/checkins", this::list);
        routes.delete("/v1/checkins/{id}", this::delete);
    }

    private void create(Context ctx) {
        Optional<String> accountId = resolveAccount(ctx);
        if (accountId.isEmpty()) return;

        PaycheckCheckIn checkIn = ctx.bodyAsClass(PaycheckCheckIn.class);
        validator.validate(checkIn);
        requireIsoDate(checkIn.getPayDate());

        // Server-stamped: a client clock that is wrong or hostile must not decide when a
        // record was made. payDate is the user's claim; recordedAt is ours.
        checkIn.setRecordedAt(Instant.now().toString());

        PaycheckCheckIn saved = store.save(accountId.get(), checkIn);
        log.info("check-in recorded: hasExpected={} hasBreakdown={}",
                checkIn.getExpectedNet() != null, checkIn.getGrossPay() != null);
        ctx.status(HttpStatus.CREATED).json(saved);
    }

    private void list(Context ctx) {
        Optional<String> accountId = resolveAccount(ctx);
        if (accountId.isEmpty()) return;

        int limit = clampLimit(ctx.queryParam("limit"));
        List<PaycheckCheckIn> items = store.list(accountId.get(), limit);
        log.info("check-ins listed: count={}", items.size());
        ctx.json(Map.of("checkIns", items, "count", items.size()));
    }

    private void delete(Context ctx) {
        Optional<String> accountId = resolveAccount(ctx);
        if (accountId.isEmpty()) return;

        boolean removed = store.delete(accountId.get(), ctx.pathParam("id"));
        if (!removed) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of(ApiConstants.ERROR, "Check-in not found"));
            return;
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * Resolves the caller to an accountId, writing the response itself when it cannot.
     * Empty means a response has already been sent and the caller must return.
     */
    private Optional<String> resolveAccount(Context ctx) {
        Optional<String> userId = AuthMiddleware.requireUser(ctx);
        if (userId.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> accountId = accounts.findAccountIdBySub(userId.get());
        if (accountId.isEmpty()) {
            ctx.status(HttpStatus.CONFLICT)
                    .json(Map.of(ApiConstants.ERROR, "No account record yet. Sign in again and retry."));
        }
        return accountId;
    }

    /**
     * A pay date is an identity here — it is the Firestore document id — so a malformed one
     * would create an unreachable entry rather than fail loudly.
     */
    private static void requireIsoDate(String payDate) {
        try {
            LocalDate.parse(payDate);
        } catch (DateTimeParseException e) {
            throw new ValidationException(Map.of("payDate", "must be an ISO date, yyyy-MM-dd"));
        }
    }

    private static int clampLimit(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_LIMIT;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed < 1 ? DEFAULT_LIMIT : Math.min(parsed, MAX_LIMIT);
        } catch (NumberFormatException e) {
            return DEFAULT_LIMIT;
        }
    }
}
