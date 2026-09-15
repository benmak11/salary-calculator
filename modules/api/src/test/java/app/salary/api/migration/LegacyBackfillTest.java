package app.salary.api.migration;

import app.salary.api.store.AccountDirectory;
import app.salary.api.store.BudgetStore;
import app.salary.api.store.CalculationStore;
import app.salary.api.store.GrantStore;
import app.salary.api.store.InMemoryAccountDirectory;
import app.salary.api.store.InMemoryBudgetStore;
import app.salary.api.store.InMemoryCalculationStore;
import app.salary.api.store.InMemoryGrantStore;
import app.salary.api.store.InMemoryUserDirectory;
import app.salary.common.constants.Country;
import app.salary.common.dto.Budget;
import app.salary.common.dto.CalculateRequest;
import app.salary.common.dto.CalculateResponse;
import app.salary.common.dto.RsuGrant;
import app.salary.common.dto.SavedCalculationSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-1b Phase 2. Each test pins one of the properties the migration plan requires, because
 * each is the kind that only fails in production: against real data, with no undo.
 */
class LegacyBackfillTest {

    private InMemoryUserDirectory users;
    private InMemoryAccountDirectory accounts;
    private InMemoryCalculationStore sourceCalcs;
    private InMemoryCalculationStore targetCalcs;
    private InMemoryGrantStore sourceGrants;
    private InMemoryGrantStore targetGrants;
    private InMemoryBudgetStore sourceBudgets;
    private InMemoryBudgetStore targetBudgets;
    private List<String> progressLines;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserDirectory();
        accounts = new InMemoryAccountDirectory();
        sourceCalcs = new InMemoryCalculationStore();
        targetCalcs = new InMemoryCalculationStore();
        sourceGrants = new InMemoryGrantStore();
        targetGrants = new InMemoryGrantStore();
        sourceBudgets = new InMemoryBudgetStore();
        targetBudgets = new InMemoryBudgetStore();
        progressLines = new ArrayList<>();
    }

    private LegacyBackfill backfill(boolean dryRun) {
        return new LegacyBackfill(users, accounts,
                new LegacyBackfill.Stores(sourceCalcs, sourceGrants, sourceBudgets),
                new LegacyBackfill.Stores(targetCalcs, targetGrants, targetBudgets),
                dryRun, (sub, accountId, created, c, g, b) ->
                        progressLines.add(sub + "\t" + accountId + "\t" + created));
    }

    private static CalculateRequest request() {
        CalculateRequest r = new CalculateRequest();
        r.setCountry(Country.US);
        return r;
    }

    private static RsuGrant grant(String ticker, String createdAt) {
        RsuGrant g = new RsuGrant();
        g.setTicker(ticker);
        g.setId("g_" + ticker);
        g.setCreatedAt(createdAt);
        return g;
    }

    /** A legacy user: signed in before the identity schema, so no identity record. */
    private void legacyUser(String sub) {
        users.upsertOnSignIn(sub, "Alex Carter");
        sourceCalcs.saveAt(sub, "calc-" + sub, Instant.parse("2026-03-01T10:00:00Z"), request(), new CalculateResponse());
        sourceGrants.put(sub, grant("AAPL", "2026-01-01T00:00:00Z"));
        sourceGrants.put(sub, grant("MSFT", "2026-02-01T00:00:00Z"));
        sourceBudgets.save(sub, new Budget());
    }

    // ── the copy is faithful ─────────────────────────────────────────────────

    @Test
    void copiesEveryCollectionPreservingIdsAndTimestamps() {
        legacyUser("sub-1");

        LegacyBackfill.Report report = backfill(false).run();

        String accountId = accounts.findAccountIdBySub("sub-1")
                .orElseGet(() -> progressLines.get(0).split("\t")[1]);
        assertEquals(1, report.users());
        assertEquals(1, report.accountsCreated());

        var calc = targetCalcs.get(accountId, "calc-sub-1").orElseThrow().getSummary();
        assertEquals("calc-sub-1", calc.getId());
        assertEquals("2026-03-01T10:00:00Z", calc.getSavedAt(), "history orders on this");

        List<RsuGrant> grants = targetGrants.list(accountId);
        assertEquals(List.of("g_AAPL", "g_MSFT"), grants.stream().map(RsuGrant::getId).toList());
        assertEquals("2026-01-01T00:00:00Z", grants.get(0).getCreatedAt(), "grant order survives");

        assertTrue(targetBudgets.get(accountId).isPresent());
    }

    @Test
    void aUserWhoAlreadyHasAnIdentityIsCopiedUnderThatAccountNotANewOne() {
        String existing = accounts.resolveOrCreate(AccountDirectory.PROVIDER_APPLE, "sub-2", "Sam");
        legacyUser("sub-2");

        LegacyBackfill.Report report = backfill(false).run();

        assertEquals(0, report.accountsCreated());
        assertTrue(targetCalcs.get(existing, "calc-sub-2").isPresent());
    }

    // ── the plan's non-negotiables ───────────────────────────────────────────

    @Test
    void dryRunReadsEverythingAndWritesNothing() {
        legacyUser("sub-3");

        LegacyBackfill.Report report = backfill(true).run();

        assertEquals(1, report.users());
        assertEquals(1, report.calculations());
        assertEquals(2, report.grants());
        assertEquals(1, report.budgets());
        // Nothing landed anywhere, and no account was minted.
        assertTrue(accounts.findAccountIdBySub("sub-3").isEmpty());
        assertTrue(targetGrants.list("(would create)").isEmpty());
        assertTrue(progressLines.get(0).contains("(would create)"));
    }

    @Test
    void runningTwiceConvergesRatherThanDuplicating() {
        legacyUser("sub-4");

        backfill(false).run();
        LegacyBackfill.Report second = backfill(false).run();

        String accountId = progressLines.get(0).split("\t")[1];
        assertEquals(accountId, progressLines.get(1).split("\t")[1], "same account both times");
        assertEquals(0, second.accountsCreated());
        assertEquals(2, targetGrants.list(accountId).size(), "not four");
        assertEquals(1, targetCalcs.list(accountId, 10, null).getItems().size());
    }

    @Test
    void resumesStrictlyAfterTheGivenCursor() {
        legacyUser("sub-a");
        legacyUser("sub-b");
        legacyUser("sub-c");

        LegacyBackfill.Report report = backfill(false).run("sub-a");

        assertEquals(2, report.users());
        assertEquals(List.of("sub-b", "sub-c"),
                progressLines.stream().map(l -> l.split("\t")[0]).toList());
        assertEquals("sub-c", report.lastSub());
    }

    @Test
    void neverWritesToTheSourceStores() {
        // Seed first, then arm: the guard must only fire on writes the BACKFILL makes.
        boolean[] armed = {false};
        CalculationStore guardedCalcs = new InMemoryCalculationStore() {
            @Override
            public SavedCalculationSummary saveAt(String u, String id, Instant at, CalculateRequest r, CalculateResponse s) {
                if (armed[0]) throw new AssertionError("wrote to the SOURCE calculation store");
                return super.saveAt(u, id, at, r, s);
            }
        };
        GrantStore guardedGrants = new InMemoryGrantStore() {
            @Override
            public RsuGrant put(String u, RsuGrant g) {
                if (armed[0]) throw new AssertionError("wrote to the SOURCE grant store");
                return super.put(u, g);
            }
        };
        BudgetStore guardedBudgets = new InMemoryBudgetStore() {
            @Override
            public Budget save(String u, Budget b) {
                if (armed[0]) throw new AssertionError("wrote to the SOURCE budget store");
                return super.save(u, b);
            }
        };
        users.upsertOnSignIn("sub-5", "Alex Carter");
        guardedCalcs.save("sub-5", request(), new CalculateResponse());
        guardedGrants.put("sub-5", grant("AAPL", "2026-01-01T00:00:00Z"));
        guardedBudgets.save("sub-5", new Budget());
        armed[0] = true;

        LegacyBackfill run = new LegacyBackfill(users, accounts,
                new LegacyBackfill.Stores(guardedCalcs, guardedGrants, guardedBudgets),
                new LegacyBackfill.Stores(targetCalcs, targetGrants, targetBudgets),
                false, (a, b, c, d, e, f) -> { });

        LegacyBackfill.Report report = run.run();   // any source write throws

        assertEquals(1, report.calculations());
        assertEquals(1, report.grants());
        assertEquals(1, report.budgets());
        String accountId = accounts.findAccountIdBySub("sub-5").orElseThrow();
        assertFalse(targetCalcs.list(accountId, 10, null).getItems().isEmpty());
    }

    @Test
    void aCalculationListedButGoneByReadTimeIsSkippedNotFatal() {
        users.upsertOnSignIn("sub-6", null);
        CalculationStore flaky = new InMemoryCalculationStore() {
            @Override
            public Optional<app.salary.common.dto.SavedCalculationDetail> get(String u, String id) {
                return Optional.empty();
            }
        };
        flaky.save("sub-6", request(), new CalculateResponse());

        LegacyBackfill run = new LegacyBackfill(users, accounts,
                new LegacyBackfill.Stores(flaky, sourceGrants, sourceBudgets),
                new LegacyBackfill.Stores(targetCalcs, targetGrants, targetBudgets),
                false, (a, b, c, d, e, f) -> { });

        LegacyBackfill.Report report = run.run();
        assertEquals(0, report.calculations());
        assertEquals(1, report.users());
    }

    @Test
    void aUserWithMoreCalculationsThanOnePageLosesNone() {
        // The list endpoint pages at 100. Before this was fixed neither store honoured the
        // cursor, so calculation #101 onward would have been silently left behind - and once
        // the old layout is deleted in the final phase, that is permanent.
        users.upsertOnSignIn("sub-big", null);
        int total = 237;
        for (int i = 0; i < total; i++) {
            sourceCalcs.saveAt("sub-big", String.format("calc-%04d", i),
                    Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i), request(), new CalculateResponse());
        }

        LegacyBackfill.Report report = backfill(false).run();

        assertEquals(total, report.calculations());
        String accountId = accounts.findAccountIdBySub("sub-big").orElseThrow();
        int seen = 0;
        String cursor = null;
        do {
            var page = targetCalcs.list(accountId, 100, cursor);
            seen += page.getItems().size();
            cursor = page.getNextCursor();
        } while (cursor != null);
        assertEquals(total, seen, "every calculation must reach the account-keyed layout");
        assertTrue(targetCalcs.get(accountId, "calc-0000").isPresent(), "the oldest one too");
    }

    @Test
    void anEmptyDirectoryIsANoOp() {
        LegacyBackfill.Report report = backfill(false).run();
        assertEquals(0, report.users());
        assertTrue(progressLines.isEmpty());
    }
}
