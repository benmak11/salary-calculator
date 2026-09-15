package app.salary.api.store;

import app.salary.common.constants.Country;
import app.salary.common.dto.Budget;
import app.salary.common.dto.CalculateRequest;
import app.salary.common.dto.CalculateResponse;
import app.salary.common.dto.RsuGrant;
import app.salary.common.dto.SavedCalculationSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-1b Phase 1.
 *
 * <p>Every operation is exercised in both flag states, because the two are not symmetrical
 * in the way that matters: before the read flip the sub-keyed layout is authoritative and
 * the account-keyed one is being built up, and afterwards the roles swap and the sub-keyed
 * layout is what a rollback falls back onto. A test that only covered one direction would
 * pass while the rollback path was broken.
 */
class DualWriteStoreTest {

    private static final String SUB = "apple-sub-1";

    private InMemoryAccountDirectory accounts;
    private AccountIdResolver resolver;
    private InMemoryCalculationStore subCalcs;
    private InMemoryCalculationStore acctCalcs;
    private InMemoryGrantStore subGrants;
    private InMemoryGrantStore acctGrants;
    private InMemoryBudgetStore subBudgets;
    private InMemoryBudgetStore acctBudgets;

    @BeforeEach
    void setUp() {
        accounts = new InMemoryAccountDirectory();
        resolver = new AccountIdResolver(accounts);
        subCalcs = new InMemoryCalculationStore();
        acctCalcs = new InMemoryCalculationStore();
        subGrants = new InMemoryGrantStore();
        acctGrants = new InMemoryGrantStore();
        subBudgets = new InMemoryBudgetStore();
        acctBudgets = new InMemoryBudgetStore();
    }

    private String linkAccount() {
        return accounts.resolveOrCreate(AccountDirectory.PROVIDER_APPLE, SUB, "Alex Carter");
    }

    private CalculationStore calcs(boolean readAccountKeyed) {
        return new DualWriteCalculationStore(subCalcs, acctCalcs, resolver, readAccountKeyed);
    }

    private GrantStore grants(boolean readAccountKeyed) {
        return new DualWriteGrantStore(subGrants, acctGrants, resolver, readAccountKeyed);
    }

    private BudgetStore budgets(boolean readAccountKeyed) {
        return new DualWriteBudgetStore(subBudgets, acctBudgets, resolver, readAccountKeyed);
    }

    private static CalculateRequest request() {
        CalculateRequest r = new CalculateRequest();
        r.setCountry(Country.US);
        return r;
    }

    private static RsuGrant grant(String ticker) {
        RsuGrant g = new RsuGrant();
        g.setTicker(ticker);
        return g;
    }

    private static List<String> ids(List<RsuGrant> grants) {
        return grants.stream().map(RsuGrant::getId).toList();
    }

    // ────────────────────────────────────────────────────────────────────────
    @Nested
    class Calculations {

        @Test
        void saveLandsInBothLayoutsWithTheSameIdAndTimestamp() {
            String accountId = linkAccount();
            SavedCalculationSummary saved = calcs(false).save(SUB, request(), new CalculateResponse());

            var fromSub = subCalcs.get(SUB, saved.getId()).orElseThrow().getSummary();
            var fromAccount = acctCalcs.get(accountId, saved.getId()).orElseThrow().getSummary();
            assertEquals(fromSub.getId(), fromAccount.getId());
            assertEquals(fromSub.getSavedAt(), fromAccount.getSavedAt());
        }

        @Test
        void saveAtLandsInBothLayouts() {
            String accountId = linkAccount();
            Instant at = Instant.parse("2026-09-15T10:00:00Z");

            calcs(false).saveAt(SUB, "calc-1", at, request(), new CalculateResponse());

            assertTrue(subCalcs.get(SUB, "calc-1").isPresent());
            assertTrue(acctCalcs.get(accountId, "calc-1").isPresent());
        }

        @Test
        void afterTheFlipSaveIsAuthoritativeOnAccountAndStillMirroredToSub() {
            String accountId = linkAccount();
            SavedCalculationSummary saved = calcs(true).save(SUB, request(), new CalculateResponse());

            assertTrue(acctCalcs.get(accountId, saved.getId()).isPresent());
            // What keeps rollback free.
            assertTrue(subCalcs.get(SUB, saved.getId()).isPresent());
        }

        @Test
        void afterTheFlipSaveAtIsMirroredBackToSub() {
            linkAccount();
            calcs(true).saveAt(SUB, "calc-2", Instant.parse("2026-09-15T10:00:00Z"),
                    request(), new CalculateResponse());
            assertTrue(subCalcs.get(SUB, "calc-2").isPresent());
        }

        @Test
        void listAndGetReadTheAuthoritativeLayout() {
            String accountId = linkAccount();
            subCalcs.saveAt(SUB, "only-in-sub", Instant.now(), request(), new CalculateResponse());
            acctCalcs.saveAt(accountId, "only-in-account", Instant.now(), request(), new CalculateResponse());

            assertEquals(1, calcs(false).list(SUB, 10, null).getItems().size());
            assertTrue(calcs(false).get(SUB, "only-in-sub").isPresent());
            assertTrue(calcs(false).get(SUB, "only-in-account").isEmpty());

            assertTrue(calcs(true).get(SUB, "only-in-account").isPresent());
            assertTrue(calcs(true).get(SUB, "only-in-sub").isEmpty());
        }

        @Test
        void deleteRemovesFromBothLayoutsInEitherDirection() {
            String accountId = linkAccount();
            SavedCalculationSummary saved = calcs(false).save(SUB, request(), new CalculateResponse());

            assertTrue(calcs(false).delete(SUB, saved.getId()));
            assertTrue(subCalcs.get(SUB, saved.getId()).isEmpty());
            assertTrue(acctCalcs.get(accountId, saved.getId()).isEmpty());

            SavedCalculationSummary again = calcs(true).save(SUB, request(), new CalculateResponse());
            assertTrue(calcs(true).delete(SUB, again.getId()));
            assertTrue(subCalcs.get(SUB, again.getId()).isEmpty());
            assertTrue(acctCalcs.get(accountId, again.getId()).isEmpty());
        }

        @Test
        void deleteAllClearsBothLayoutsInEitherDirection() {
            String accountId = linkAccount();
            calcs(false).save(SUB, request(), new CalculateResponse());
            calcs(false).save(SUB, request(), new CalculateResponse());

            assertEquals(2, calcs(false).deleteAll(SUB));
            assertEquals(0, subCalcs.list(SUB, 10, null).getItems().size());
            assertEquals(0, acctCalcs.list(accountId, 10, null).getItems().size());

            calcs(true).save(SUB, request(), new CalculateResponse());
            assertEquals(1, calcs(true).deleteAll(SUB));
            assertEquals(0, subCalcs.list(SUB, 10, null).getItems().size());
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    @Nested
    class Grants {

        @Test
        void createLandsInBothLayoutsUnderOneId() {
            String accountId = linkAccount();
            RsuGrant created = grants(false).create(SUB, grant("AAPL"));

            assertEquals(List.of(created.getId()), ids(subGrants.list(SUB)));
            assertEquals(List.of(created.getId()), ids(acctGrants.list(accountId)));
        }

        @Test
        void afterTheFlipCreateIsStillMirroredToSub() {
            String accountId = linkAccount();
            RsuGrant created = grants(true).create(SUB, grant("MSFT"));

            assertEquals(List.of(created.getId()), ids(acctGrants.list(accountId)));
            assertEquals(List.of(created.getId()), ids(subGrants.list(SUB)));
        }

        @Test
        void putWritesBothLayoutsInEitherDirection() {
            String accountId = linkAccount();
            RsuGrant g = grant("NVDA");
            g.setId("g_fixed");

            grants(false).put(SUB, g);
            assertEquals(List.of("g_fixed"), ids(subGrants.list(SUB)));
            assertEquals(List.of("g_fixed"), ids(acctGrants.list(accountId)));

            subGrants.deleteAll(SUB);
            grants(true).put(SUB, g);
            assertEquals(List.of("g_fixed"), ids(subGrants.list(SUB)));
        }

        @Test
        void listReadsTheAuthoritativeLayout() {
            String accountId = linkAccount();
            RsuGrant onlySub = grant("SUB");
            onlySub.setId("g_sub");
            subGrants.put(SUB, onlySub);
            RsuGrant onlyAccount = grant("ACCT");
            onlyAccount.setId("g_acct");
            acctGrants.put(accountId, onlyAccount);

            assertEquals(List.of("g_sub"), ids(grants(false).list(SUB)));
            assertEquals(List.of("g_acct"), ids(grants(true).list(SUB)));
        }

        @Test
        void updateMirrorsWithPutSoAGrantMissingFromTheMirrorIsCreatedThere() {
            String accountId = linkAccount();
            // Created before dual-write was switched on: present in sub only.
            RsuGrant existing = grant("OLD");
            existing.setId("g_old");
            subGrants.put(SUB, existing);

            Optional<RsuGrant> updated = grants(false).update(SUB, "g_old", grant("NEW"));

            assertTrue(updated.isPresent());
            // update() alone would have refused to create this; put() does not.
            assertEquals(List.of("g_old"), ids(acctGrants.list(accountId)));
        }

        @Test
        void updateOfAMissingGrantMirrorsNothing() {
            String accountId = linkAccount();
            assertTrue(grants(false).update(SUB, "nope", grant("X")).isEmpty());
            assertTrue(acctGrants.list(accountId).isEmpty());
        }

        @Test
        void afterTheFlipUpdateIsMirroredBackToSub() {
            String accountId = linkAccount();
            RsuGrant existing = grant("OLD");
            existing.setId("g_x");
            acctGrants.put(accountId, existing);

            assertTrue(grants(true).update(SUB, "g_x", grant("NEW")).isPresent());
            assertEquals(List.of("g_x"), ids(subGrants.list(SUB)));
        }

        @Test
        void deleteAndDeleteAllClearBothLayouts() {
            String accountId = linkAccount();
            RsuGrant created = grants(false).create(SUB, grant("NVDA"));

            assertTrue(grants(false).delete(SUB, created.getId()));
            assertTrue(subGrants.list(SUB).isEmpty());
            assertTrue(acctGrants.list(accountId).isEmpty(),
                    "a survivor here outlives the account that owned it");

            grants(true).create(SUB, grant("AMD"));
            assertEquals(1, grants(true).deleteAll(SUB));
            assertTrue(subGrants.list(SUB).isEmpty());
            assertTrue(acctGrants.list(accountId).isEmpty());
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    @Nested
    class Budgets {

        @Test
        void saveGetAndDeleteSpanBothLayouts() {
            String accountId = linkAccount();

            assertNotNull(budgets(false).save(SUB, new Budget()));
            assertTrue(subBudgets.get(SUB).isPresent());
            assertTrue(acctBudgets.get(accountId).isPresent());
            assertTrue(budgets(false).get(SUB).isPresent());

            assertTrue(budgets(false).delete(SUB));
            assertTrue(subBudgets.get(SUB).isEmpty());
            assertTrue(acctBudgets.get(accountId).isEmpty());
        }

        @Test
        void afterTheFlipBudgetsReadFromAccountAndStillMirrorToSub() {
            String accountId = linkAccount();

            budgets(true).save(SUB, new Budget());
            assertTrue(acctBudgets.get(accountId).isPresent());
            assertTrue(subBudgets.get(SUB).isPresent());
            assertTrue(budgets(true).get(SUB).isPresent());

            assertTrue(budgets(true).delete(SUB));
            assertTrue(subBudgets.get(SUB).isEmpty());
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    @Nested
    class FailureAndFallback {

        @Test
        void aFailingMirrorDoesNotBreakTheAuthoritativeWrite() {
            linkAccount();
            BudgetStore exploding = new InMemoryBudgetStore() {
                @Override
                public Budget save(String userId, Budget budget) {
                    throw new IllegalStateException("mirror is down");
                }
            };

            Budget saved = new DualWriteBudgetStore(subBudgets, exploding, resolver, false)
                    .save(SUB, new Budget());

            assertNotNull(saved);
            assertTrue(subBudgets.get(SUB).isPresent(), "the authoritative write must have landed");
        }

        @Test
        void aFailingAuthoritativeWriteStillPropagates() {
            linkAccount();
            BudgetStore exploding = new InMemoryBudgetStore() {
                @Override
                public Budget save(String userId, Budget budget) {
                    throw new IllegalStateException("primary is down");
                }
            };
            BudgetStore store = new DualWriteBudgetStore(exploding, acctBudgets, resolver, false);

            IllegalStateException thrown =
                    assertThrows(IllegalStateException.class, () -> store.save(SUB, new Budget()));
            assertEquals("primary is down", thrown.getMessage());
        }

        @Test
        void anUnparseableSavedAtSkipsTheMirrorRatherThanFailingTheSave() {
            String accountId = linkAccount();
            CalculationStore broken = new InMemoryCalculationStore() {
                @Override
                public SavedCalculationSummary save(String userId, CalculateRequest req, CalculateResponse res) {
                    SavedCalculationSummary summary = super.save(userId, req, res);
                    summary.setSavedAt("not-a-timestamp");
                    return summary;
                }
            };

            SavedCalculationSummary saved = new DualWriteCalculationStore(broken, acctCalcs, resolver, false)
                    .save(SUB, request(), new CalculateResponse());

            assertNotNull(saved);
            // Skipped, not written at a made-up time: the parity check reports a missing row,
            // but cannot see a row that merely disagrees on ordering.
            assertTrue(acctCalcs.get(accountId, saved.getId()).isEmpty());
        }

        @Test
        void aSubWithNoAccountIdStillWritesAndReads() {
            // Everyone whose last sign-in predates the identity schema.
            SavedCalculationSummary saved = calcs(false).save(SUB, request(), new CalculateResponse());

            assertTrue(subCalcs.get(SUB, saved.getId()).isPresent());
            assertEquals(1, calcs(false).list(SUB, 10, null).getItems().size());
            assertTrue(acctCalcs.get("unused", saved.getId()).isEmpty());
        }

        @Test
        void readsFallBackToSubKeyedWhenTheSubHasNoAccountId() {
            // Phase 3 with a user the backfill missed: reading the account-keyed layout
            // would show an empty history, so the decorator falls back instead.
            subCalcs.save(SUB, request(), new CalculateResponse());
            assertEquals(1, calcs(true).list(SUB, 10, null).getItems().size());
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    @Nested
    class Resolver {

        @Test
        void resolvesAndCachesAKnownSub() {
            String accountId = linkAccount();
            assertEquals(Optional.of(accountId), resolver.resolve(SUB));
            assertEquals(Optional.of(accountId), resolver.resolve(SUB), "second call is cached");
        }

        @Test
        void doesNotCacheAMissSoANewAccountIsSeenImmediately() {
            assertFalse(resolver.resolve(SUB).isPresent());
            String accountId = linkAccount();
            assertEquals(Optional.of(accountId), resolver.resolve(SUB));
        }

        @Test
        void forgetsASubAfterItsAccountIsDeleted() {
            linkAccount();
            assertTrue(resolver.resolve(SUB).isPresent());

            accounts.deleteByProviderSub(SUB);
            resolver.forget(SUB);

            assertEquals(Optional.empty(), resolver.resolve(SUB));
        }

        @Test
        void toleratesNoDirectoryAndBlankInput() {
            AccountIdResolver none = new AccountIdResolver(null);
            assertEquals(Optional.empty(), none.resolve(SUB));
            assertEquals(Optional.empty(), resolver.resolve(null));
            assertEquals(Optional.empty(), resolver.resolve("  "));
            resolver.forget(null);
        }
    }
}
