package app.salary.api.migration;

import org.junit.jupiter.api.Test;

import static app.salary.api.migration.MigrationMode.ACCOUNT_KEYED_ONLY;
import static app.salary.api.migration.MigrationMode.DUAL_WRITE;
import static app.salary.api.migration.MigrationMode.SUB_KEYED_ONLY;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The flag combinations, walked in the order the migration plan walks them.
 *
 * <p>These exist because the last transition was got wrong in production on 2026-10-01:
 * turning dual-write off was treated as "no migration wiring", which would have left reads
 * on a sub-keyed layout that had just stopped being written.
 */
class MigrationModeTest {

    @Test
    void beforePhase1NothingIsMigrated() {
        assertEquals(SUB_KEYED_ONLY, MigrationMode.from(false, false, true));
    }

    @Test
    void phase1WritesBothAndStillReadsTheOldLayout() {
        assertEquals(DUAL_WRITE, MigrationMode.from(true, false, true));
    }

    @Test
    void phase3StillWritesBothButReadsTheNewLayout() {
        assertEquals(DUAL_WRITE, MigrationMode.from(true, true, true));
    }

    @Test
    void phase5IsAccountKeyedOnlyRatherThanNoWiringAtAll() {
        // The one that broke. "Dual-write off" is not "stop migrating": the stores have to
        // be swapped for the account-keyed ones, or reads fall back to a layout that is no
        // longer written and every user's recent data appears to vanish.
        assertEquals(ACCOUNT_KEYED_ONLY, MigrationMode.from(false, true, true));
    }

    @Test
    void withoutFirestoreTheFlagsAreMeaninglessRatherThanWrong() {
        // Local dev and tests use in-memory stores, which have no layout to choose.
        for (boolean dualWrite : new boolean[] {false, true}) {
            for (boolean read : new boolean[] {false, true}) {
                assertEquals(SUB_KEYED_ONLY, MigrationMode.from(dualWrite, read, false),
                        "dualWrite=" + dualWrite + " read=" + read);
            }
        }
    }
}
