package app.salary.api.store;

import app.salary.common.dto.RsuGrant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryGrantStoreTest {

    private InMemoryGrantStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryGrantStore();
    }

    private static RsuGrant grant(String ticker) {
        RsuGrant.VestingSchedule schedule = new RsuGrant.VestingSchedule();
        schedule.setPresetId("monthly1cliff");
        schedule.setTotalMonths(48);
        schedule.setCliffMonths(12);
        schedule.setFreqMonths(1);
        RsuGrant g = new RsuGrant();
        g.setTicker(ticker);
        g.setCompany(ticker + " Inc.");
        g.setSharesTotal(400.0);
        g.setPricePerShare(232.14);
        g.setGrantDate("2025-03-15");
        g.setSchedule(schedule);
        return g;
    }

    @Test
    void create_assignsIdAndListsOldestFirst() {
        RsuGrant first = store.create("user-1", grant("AAPL"));
        RsuGrant second = store.create("user-1", grant("RDDT"));

        assertNotNull(first.getId());
        assertNotNull(second.getId());
        assertNotEquals(first.getId(), second.getId());

        List<RsuGrant> listed = store.list("user-1");
        assertEquals(2, listed.size());
        assertEquals("AAPL", listed.get(0).getTicker());
        assertEquals("RDDT", listed.get(1).getTicker());
    }

    @Test
    void list_unknownUser_returnsEmpty() {
        assertTrue(store.list("nobody").isEmpty());
    }

    @Test
    void update_existingGrant_replacesAndKeepsId() {
        RsuGrant created = store.create("user-1", grant("AAPL"));
        RsuGrant edited = grant("AAPL");
        edited.setSharesTotal(500.0);

        Optional<RsuGrant> updated = store.update("user-1", created.getId(), edited);

        assertTrue(updated.isPresent());
        assertEquals(created.getId(), updated.get().getId());
        assertEquals(500.0, store.list("user-1").get(0).getSharesTotal());
    }

    @Test
    void update_missingGrant_returnsEmpty() {
        assertTrue(store.update("user-1", "g_missing", grant("AAPL")).isEmpty());
    }

    @Test
    void delete_existingGrant_removesIt() {
        RsuGrant created = store.create("user-1", grant("AAPL"));

        assertTrue(store.delete("user-1", created.getId()));
        assertTrue(store.list("user-1").isEmpty());
        assertFalse(store.delete("user-1", created.getId()));
    }

    @Test
    void deleteAll_removesOnlyThatUsersGrants() {
        store.create("user-1", grant("AAPL"));
        store.create("user-1", grant("RDDT"));
        store.create("user-2", grant("MSFT"));

        assertEquals(2, store.deleteAll("user-1"));
        assertTrue(store.list("user-1").isEmpty());
        assertEquals(1, store.list("user-2").size());
        assertEquals(0, store.deleteAll("user-1"));
    }
}
