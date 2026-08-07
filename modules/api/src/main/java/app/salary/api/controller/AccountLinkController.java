package app.salary.api.controller;

import app.salary.api.auth.AuthMiddleware;
import app.salary.api.ratelimit.CallerKey;
import app.salary.api.ratelimit.RateLimiter;
import app.salary.api.service.EntitlementService;
import app.salary.api.store.AccountDirectory;
import app.salary.api.store.LinkCode;
import app.salary.api.store.LinkCodeStore;
import app.salary.common.constants.ApiConstants;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Link codes: the primitive that lets one human with an Apple sign-in and a Google sign-in
 * resolve to a single account.
 *
 * <p>Generate on the device that is already signed in, read the six digits across to the
 * other device, redeem there. Both routes require auth, because both sides of a link are by
 * definition signed-in users.
 *
 * <p><b>Ships dark.</b> Linking works and is durable, but has no visible effect yet: saved
 * calculations, grants and budgets are still keyed on the provider sub, so a linked pair
 * still reads two histories until the B1 migration re-keys them. What linking establishes
 * now is the identity graph that migration will consolidate against, plus the entitlement
 * merge, which matters the moment subscriptions exist.
 *
 * <p>The same primitive is what Household Mode is built on later. It is not throwaway
 * paywall plumbing.
 *
 * <h2>Why the attempt counter is on the code</h2>
 * Six digits is 10^6, so the redeem route carries its own tight limiter (10/min, against a
 * 300/min default). <b>That limiter, not the per-code counter, is what bounds enumeration:</b>
 * an attacker sweeping the space tries a different code each time and never accumulates
 * attempts against any one of them. The per-code counter covers the narrower case of someone
 * hammering a code they partially saw, burning it after five failures.
 */
public class AccountLinkController {
    private static final Logger log = LoggerFactory.getLogger(AccountLinkController.class);

    private static final String FIELD_CODE = "code";

    private final LinkCodeStore linkCodes;
    private final AccountDirectory accounts;
    private final EntitlementService entitlements;
    private final RateLimiter redeemLimiter;

    /** {@code redeemLimiter} may be null only when rate limiting is disabled wholesale. */
    public AccountLinkController(LinkCodeStore linkCodes, AccountDirectory accounts,
                                 EntitlementService entitlements, RateLimiter redeemLimiter) {
        this.linkCodes = linkCodes;
        this.accounts = accounts;
        this.entitlements = entitlements;
        this.redeemLimiter = redeemLimiter;
    }

    public void register(RoutesConfig routes) {
        routes.post("/v1/account/link-code", this::generate);
        routes.post("/v1/account/link", this::redeem);
    }

    private void generate(Context ctx) {
        Optional<String> userId = AuthMiddleware.requireUser(ctx);
        if (userId.isEmpty()) return;

        Optional<String> accountId = accounts.findAccountIdBySub(userId.get());
        if (accountId.isEmpty()) {
            // Pre-schema users have no account to link to yet; one more sign-in fixes it.
            ctx.status(HttpStatus.CONFLICT)
                    .json(Map.of(ApiConstants.ERROR, "No account record yet. Sign in again and retry."));
            return;
        }

        LinkCode issued = linkCodes.issue(accountId.get());
        log.info("link code issued: expiresInSeconds={}",
                issued.expiresAt().getEpochSecond() - issued.createdAt().getEpochSecond());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put(FIELD_CODE, issued.code());
        body.put("expiresAt", issued.expiresAt().toString());
        ctx.status(HttpStatus.CREATED).json(body);
    }

    private void redeem(Context ctx) {
        Optional<String> userId = AuthMiddleware.requireUser(ctx);
        if (userId.isEmpty()) return;

        // Throttled here rather than in the blanket middleware: this route needs a far
        // tighter budget than everything else, and this is where its path is defined.
        if (redeemLimiter != null && !redeemLimiter.tryAcquire(CallerKey.of(ctx))) {
            log.warn("link redemption throttled");
            ctx.header("Retry-After", "60");
            ctx.status(HttpStatus.TOO_MANY_REQUESTS)
                    .json(Map.of(ApiConstants.ERROR, "Too many attempts. Retry in a minute."));
            return;
        }

        String code = normalize(ctx.bodyAsClass(Map.class).get(FIELD_CODE));
        if (code == null) {
            badRequest(ctx, "A six-digit code is required.");
            return;
        }

        Instant now = Instant.now();
        Optional<LinkCode> found = linkCodes.find(code);
        if (found.isEmpty() || !found.get().isUsableAt(now)) {
            // One message for unknown, expired, redeemed and burned. Distinguishing them
            // tells someone guessing which codes exist, which is the thing worth hiding.
            recordFailure(code);
            ctx.status(HttpStatus.NOT_FOUND)
                    .json(Map.of(ApiConstants.ERROR, "That code is not valid. Generate a new one."));
            return;
        }

        String targetAccountId = found.get().accountId();
        Optional<String> previous = accounts.relinkIdentity(userId.get(), targetAccountId);
        if (previous.isEmpty()) {
            // Already on the target account: linking twice is a no-op, not an error, but the
            // code is spent so it cannot be replayed by someone else.
            linkCodes.markRedeemed(code);
            ctx.json(linkedBody(targetAccountId, 0));
            return;
        }

        if (!linkCodes.markRedeemed(code)) {
            // Lost a race for a single-use code. The relink above is harmless and idempotent.
            ctx.status(HttpStatus.CONFLICT)
                    .json(Map.of(ApiConstants.ERROR, "That code was just used. Generate a new one."));
            return;
        }

        int mergedStores = entitlements == null ? 0 : entitlements.mergeInto(previous.get(), targetAccountId);
        log.info("accounts linked: mergedEntitlementStores={}", mergedStores);
        ctx.json(linkedBody(targetAccountId, mergedStores));
    }

    /**
     * Burns one attempt against the code. Deliberately runs even for codes that turn out not
     * to exist: the caller cannot tell the difference, so neither branch may be cheaper.
     */
    private void recordFailure(String code) {
        linkCodes.recordFailedAttempt(code).ifPresent(after -> {
            if (after.attempts() >= LinkCode.MAX_ATTEMPTS) {
                log.warn("link code burned after {} failed attempts", after.attempts());
            }
        });
    }

    private static Map<String, Object> linkedBody(String accountId, int mergedStores) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("linked", true);
        body.put("accountId", accountId);
        body.put("mergedEntitlementStores", mergedStores);
        return body;
    }

    private static void badRequest(Context ctx, String message) {
        ctx.status(HttpStatus.BAD_REQUEST).json(Map.of(ApiConstants.ERROR, message));
    }

    /** Accepts spacing and stray separators, since the code is read off another screen. */
    private static String normalize(Object raw) {
        if (raw == null) {
            return null;
        }
        String digits = String.valueOf(raw).replaceAll("\\D", "");
        return digits.length() == 6 ? digits : null;
    }
}
