package app.salary.api.migration;

import app.salary.api.store.AccountDirectory;
import app.salary.api.store.BudgetStore;
import app.salary.api.store.CalculationStore;
import app.salary.api.store.GrantStore;
import app.salary.api.store.UserDirectory;
import app.salary.common.dto.Budget;
import app.salary.common.dto.CalculationListResponse;
import app.salary.common.dto.RsuGrant;
import app.salary.common.dto.SavedCalculationDetail;
import app.salary.common.dto.SavedCalculationSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * B-1b Phase 2: copies every user's sub-keyed data into the account-keyed layout.
 *
 * <p>This is the logic only. {@link LegacyBackfillMain} wires it to Firestore and runs it
 * as a one-shot from a shell — <b>never from an endpoint</b>, because an HTTP-triggered
 * migration can be fired twice by a retry.
 *
 * <p>Properties the migration plan requires, and how each is met:
 * <ul>
 *   <li><b>Read-only on the source.</b> The sub-keyed stores are only ever read. Nothing
 *       here can call {@code save}, {@code put} or {@code delete} on them: the class holds
 *       them under names that make a write look wrong, and the tests assert it.</li>
 *   <li><b>Idempotent.</b> Every write is a replace at a fixed id — {@code saveAt},
 *       {@code put}, a single-document {@code save} — and account creation returns the
 *       existing account on a repeat. Running twice converges; it never duplicates.</li>
 *   <li><b>Resumable.</b> Users are walked in id order and the last id handled is handed to
 *       {@link Progress} after each one. Restart with that id as the cursor.</li>
 *   <li><b>Dry-run first.</b> {@code dryRun} reads everything and writes nothing — not even
 *       the legacy account — and reports what it would have done.</li>
 *   <li><b>Ids and timestamps survive.</b> Calculations are re-saved under their original id
 *       and {@code savedAt}; grants carry their {@code createdAt}. The history endpoints
 *       order on those, so a copy with fresh timestamps would list differently.</li>
 * </ul>
 *
 * <p>Users with no identity get one created via
 * {@link AccountDirectory#createLegacyAccount}, which deliberately records no provider —
 * see that method for why guessing it was rejected.
 */
public final class LegacyBackfill {
    private static final Logger log = LoggerFactory.getLogger(LegacyBackfill.class);

    private static final int USER_PAGE = 200;
    private static final int CALC_PAGE = 100;

    /** Receives progress as it happens. The CLI appends to a file; tests collect in memory. */
    public interface Progress {
        /** One line per user, after that user is fully done. Also the resume cursor. */
        void userDone(String sub, String accountId, boolean accountCreated,
                      int calculations, int grants, boolean budget);
    }

    /** What the run did, or would have done under dry-run. */
    public record Report(int users, int accountsCreated, int calculations, int grants, int budgets,
                         String lastSub) {
    }

    /**
     * The three per-user stores of one layout. The job copies {@code source} to
     * {@code target}, and naming the sides that way rather than sub/account is deliberate:
     * a write to {@code source.calculations()} reads as the bug it would be.
     */
    public record Stores(CalculationStore calculations, GrantStore grants, BudgetStore budgets) {
    }

    private final UserDirectory users;
    private final AccountDirectory accounts;
    private final Stores source;
    private final Stores target;
    private final boolean dryRun;
    private final Progress progress;

    public LegacyBackfill(UserDirectory users, AccountDirectory accounts,
                          Stores source, Stores target,
                          boolean dryRun, Progress progress) {
        this.users = users;
        this.accounts = accounts;
        this.source = source;
        this.target = target;
        this.dryRun = dryRun;
        this.progress = progress;
    }

    /** Runs from the start. */
    public Report run() {
        return run(null);
    }

    /** Resumes strictly after {@code afterSub} — the last id a previous run reported done. */
    public Report run(String afterSub) {
        int usersDone = 0;
        int created = 0;
        int calcs = 0;
        int grants = 0;
        int budgets = 0;
        String cursor = afterSub;
        String last = afterSub;

        while (true) {
            List<String> page = users.listUserIds(cursor, USER_PAGE);
            if (page.isEmpty()) {
                break;
            }
            for (String sub : page) {
                UserResult r = migrateUser(sub);
                usersDone++;
                if (r.accountCreated) {
                    created++;
                }
                calcs += r.calculations;
                grants += r.grants;
                if (r.budget) {
                    budgets++;
                }
                progress.userDone(sub, r.accountId, r.accountCreated, r.calculations, r.grants, r.budget);
                last = sub;
            }
            cursor = page.get(page.size() - 1);
        }

        log.info("backfill {}: users={} accountsCreated={} calculations={} grants={} budgets={}",
                dryRun ? "DRY RUN" : "complete", usersDone, created, calcs, grants, budgets);
        return new Report(usersDone, created, calcs, grants, budgets, last);
    }

    private record UserResult(String accountId, boolean accountCreated,
                              int calculations, int grants, boolean budget) {
    }

    private UserResult migrateUser(String sub) {
        Optional<String> existing = accounts.findAccountIdBySub(sub);
        boolean create = existing.isEmpty();
        String accountId;
        if (create) {
            // Dry-run must not mint anything; the id is only a label for the report.
            accountId = dryRun ? "(would create)"
                    : accounts.createLegacyAccount(sub, users.displayName(sub).orElse(null));
        } else {
            accountId = existing.get();
        }

        int calcs = copyCalculations(sub, accountId);
        int grants = copyGrants(sub, accountId);
        boolean budget = copyBudget(sub, accountId);
        return new UserResult(accountId, create, calcs, grants, budget);
    }

    private int copyCalculations(String sub, String accountId) {
        int copied = 0;
        String cursor = null;
        do {
            CalculationListResponse page = source.calculations().list(sub, CALC_PAGE, cursor);
            for (SavedCalculationSummary summary : page.getItems()) {
                Optional<SavedCalculationDetail> detail = source.calculations().get(sub, summary.getId());
                if (detail.isEmpty()) {
                    // Listed but gone by the time it was read: nothing to copy, not an error.
                    continue;
                }
                if (!dryRun) {
                    target.calculations().saveAt(accountId, summary.getId(),
                            Instant.parse(summary.getSavedAt()),
                            detail.get().getRequest(), detail.get().getResponse());
                }
                copied++;
            }
            cursor = page.getNextCursor();
        } while (cursor != null && !cursor.isBlank());
        return copied;
    }

    private int copyGrants(String sub, String accountId) {
        List<RsuGrant> grants = source.grants().list(sub);
        if (!dryRun) {
            for (RsuGrant grant : grants) {
                // put() honours the createdAt the grant carries, so ordering survives.
                target.grants().put(accountId, grant);
            }
        }
        return grants.size();
    }

    private boolean copyBudget(String sub, String accountId) {
        Optional<Budget> budget = source.budgets().get(sub);
        if (budget.isPresent() && !dryRun) {
            target.budgets().save(accountId, budget.get());
        }
        return budget.isPresent();
    }
}
