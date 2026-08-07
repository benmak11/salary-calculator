package app.salary.api.service;

import app.salary.api.store.AccountDirectory;
import app.salary.api.store.Entitlement;
import app.salary.api.store.EntitlementStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Answers "is this account Pro right now", and nothing else.
 *
 * <p>The resolution rule, from the roadmap: <b>Pro is any non-revoked entitlement with a
 * future expiry, from any store.</b> Server-wins across stores is what keeps a
 * cross-platform subscriber Pro when the store they are currently signed in on has no local
 * record of the purchase.
 *
 * <p><b>Enforcement ships off.</b> {@code SUBSCRIPTION_ENFORCEMENT=false} makes
 * {@link #isPro} irrelevant to every caller: nothing is gated, and this only records what it
 * would have decided. The switch exists so the schema, the resolution and the 402 path can
 * all be live and observed before a single user is ever refused.
 *
 * <p><b>Do not flip enforcement on before the B1 migration backfills
 * {@code legacy_pro_budget}.</b> Grandfathered budget users are identified by that flag; with
 * enforcement on and the flag unset, every existing budget user loses a feature they already
 * had, which is the precise outcome the tier model exists to avoid.
 */
public class EntitlementService {
    private static final Logger log = LoggerFactory.getLogger(EntitlementService.class);

    private final EntitlementStore entitlements;
    private final AccountDirectory accounts;
    private final boolean subscriptionEnforcement;
    private final boolean playEnforcement;

    public EntitlementService(EntitlementStore entitlements,
                              AccountDirectory accounts,
                              boolean subscriptionEnforcement,
                              boolean playEnforcement) {
        this.entitlements = entitlements;
        this.accounts = accounts;
        this.subscriptionEnforcement = subscriptionEnforcement;
        this.playEnforcement = playEnforcement;
    }

    /**
     * True when any store holds a live entitlement.
     *
     * <p>Play records are ignored entirely while {@code PLAY_ENFORCEMENT} is off, so a
     * half-built Play integration writing speculative records cannot grant Pro. App Store
     * records are unaffected: the two switches are independent because the two stores go
     * live at different times.
     */
    public boolean isPro(String accountId, Instant now) {
        if (accountId == null || accountId.isBlank()) {
            return false;
        }
        List<Entitlement> held = entitlements.findAll(accountId);
        return held.stream()
                .filter(e -> playEnforcement || !Entitlement.PLAY.equals(e.store()))
                .anyMatch(e -> e.grantsAccessAt(now));
    }

    /**
     * Whether this account keeps AI budget access regardless of subscription.
     *
     * <p>Set once, at migration, for accounts that already had a saved budget. It grants one
     * feature, not Pro: a grandfathered account still meets the wall everywhere else.
     */
    public boolean hasLegacyProBudget(String accountId) {
        return accountId != null && !accountId.isBlank() && accounts != null
                && accounts.hasLegacyProBudget(accountId);
    }

    /**
     * The gate used by Pro-only routes. Returns whether the request may proceed, and logs
     * what it would have done while enforcement is off so the refusal rate is knowable
     * before it is real.
     */
    public boolean allows(Optional<String> accountId, String feature, Instant now) {
        String id = accountId.orElse(null);
        boolean entitled = isPro(id, now)
                || (FEATURE_BUDGET_PLAN.equals(feature) && hasLegacyProBudget(id));
        if (entitled) {
            return true;
        }
        if (!subscriptionEnforcement) {
            log.info("subscription gate observed (not enforced): feature={} resolved={}",
                    feature, id != null);
            return true;
        }
        log.info("subscription required: feature={}", feature);
        return false;
    }

    /**
     * Folds one account's entitlements into another, keeping the later expiry per store.
     *
     * <p>Per store, not overall: an account with a lapsed App Store record and a live Play
     * one must end up with both, or the survivor loses whichever store it did not win. The
     * later expiry wins because a link is the same human proving they own both sides, and
     * taking the shorter of two subscriptions they paid for is indefensible.
     *
     * <p>A revoked record never overwrites a live one. Revocation is a fact about a specific
     * purchase, and merging must not let a refunded purchase on one device cancel a good
     * subscription on another.
     */
    public int mergeInto(String fromAccountId, String toAccountId) {
        if (fromAccountId == null || toAccountId == null || fromAccountId.equals(toAccountId)) {
            return 0;
        }
        List<Entitlement> incoming = entitlements.findAll(fromAccountId);
        if (incoming.isEmpty()) {
            return 0;
        }
        Map<String, Entitlement> existing = entitlements.findAll(toAccountId).stream()
                .collect(Collectors.toMap(Entitlement::store, e -> e, (a, b) -> a));

        int merged = 0;
        for (Entitlement candidate : incoming) {
            Entitlement current = existing.get(candidate.store());
            if (current == null || wins(candidate, current)) {
                entitlements.upsert(toAccountId, candidate);
                merged++;
            }
        }
        log.info("entitlements merged on link: stores={} ", merged);
        return merged;
    }

    private static boolean wins(Entitlement candidate, Entitlement current) {
        if (candidate.revoked() && !current.revoked()) {
            return false;
        }
        if (!candidate.revoked() && current.revoked()) {
            return true;
        }
        return candidate.expiresAt() != null
                && (current.expiresAt() == null || candidate.expiresAt().isAfter(current.expiresAt()));
    }

    public static final String FEATURE_BUDGET_PLAN = "budget_plan";
}
