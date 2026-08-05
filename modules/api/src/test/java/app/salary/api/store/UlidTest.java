package app.salary.api.store;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class UlidTest {

    @Test
    void generatesTwentySixCrockfordCharacters() {
        String id = Ulid.generate();
        assertEquals(26, id.length());
        assertTrue(id.matches("[0-9A-HJKMNP-TV-Z]{26}"),
                "expected Crockford base32 (no I, L, O or U), got " + id);
    }

    @Test
    void sortsLexicographicallyByTimestamp() {
        String earlier = Ulid.generate(1_700_000_000_000L);
        String later = Ulid.generate(1_800_000_000_000L);
        assertTrue(earlier.compareTo(later) < 0,
                earlier + " should sort before " + later);
    }

    @Test
    void sharesTheTimestampPrefixWithinTheSameMillisecond() {
        String a = Ulid.generate(1_700_000_000_000L);
        String b = Ulid.generate(1_700_000_000_000L);
        assertEquals(a.substring(0, 10), b.substring(0, 10));
        // Same instant, so only the entropy can distinguish them.
        assertNotEquals(a, b, "entropy should differ within a millisecond");
    }

    @Test
    void doesNotCollideAcrossManyIdsInTheSameMillisecond() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            seen.add(Ulid.generate(1_700_000_000_000L));
        }
        assertEquals(10_000, seen.size());
    }

    @Test
    void encodesTheEpochAsZeroes() {
        assertEquals("0000000000", Ulid.generate(0L).substring(0, 10));
    }
}
