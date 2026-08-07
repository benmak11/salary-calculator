package app.salary.api.store;

import app.salary.common.dto.PaycheckCheckIn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the branches the HTTP tests cannot reach: an account with nothing stored, and the
 * limit truncation that keeps a long history from being returned whole.
 */
class InMemoryCheckInStoreTest {

    private InMemoryCheckInStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryCheckInStore();
    }

    private static PaycheckCheckIn entry(String payDate, double net) {
        PaycheckCheckIn c = new PaycheckCheckIn();
        c.setPayDate(payDate);
        c.setActualNet(net);
        return c;
    }

    @Test
    void anAccountWithNothingStoredReadsEmptyRatherThanThrowing() {
        assertTrue(store.list("nobody", 10).isEmpty());
        assertTrue(store.find("nobody", "any-id").isEmpty());
        assertFalse(store.delete("nobody", "any-id"));
        assertEquals(0, store.deleteAll("nobody"));
    }

    @Test
    void listIsTruncatedToTheLimit() {
        for (int day = 1; day <= 10; day++) {
            store.save("acct-1", entry(String.format("2026-08-%02d", day), day * 100.0));
        }

        assertEquals(3, store.list("acct-1", 3).size());
        // Newest first, so a truncated list keeps the most recent paydays.
        assertEquals("2026-08-10", store.list("acct-1", 3).getFirst().getPayDate());
        assertEquals(10, store.list("acct-1", 50).size());
    }

    @Test
    void eachSavedEntryGetsItsOwnId() {
        PaycheckCheckIn first = store.save("acct-1", entry("2026-08-14", 100.0));
        PaycheckCheckIn second = store.save("acct-1", entry("2026-07-31", 200.0));

        assertNotEquals(first.getId(), second.getId());
        assertEquals(first.getId(), store.find("acct-1", first.getId()).orElseThrow().getId());
    }

    @Test
    void deleteAllReportsHowMuchItRemoved() {
        store.save("acct-1", entry("2026-08-14", 100.0));
        store.save("acct-1", entry("2026-07-31", 200.0));
        store.save("acct-2", entry("2026-08-14", 300.0));

        assertEquals(2, store.deleteAll("acct-1"));
        assertTrue(store.list("acct-1", 10).isEmpty());
        // Purging one account must not touch another's history.
        assertEquals(1, store.list("acct-2", 10).size());
    }

    @Test
    void findIgnoresAnotherAccountsEntry() {
        PaycheckCheckIn mine = store.save("acct-1", entry("2026-08-14", 100.0));
        assertTrue(store.find("acct-2", mine.getId()).isEmpty());
        assertFalse(store.delete("acct-2", mine.getId()));
    }
}
