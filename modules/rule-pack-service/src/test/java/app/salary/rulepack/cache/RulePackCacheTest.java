package app.salary.rulepack.cache;

import app.salary.rulepack.dto.RulePackDto;
import app.salary.rulepack.entity.RulePackStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RulePackCacheTest {

    private RulePackCache cache;

    @BeforeEach
    void setUp() {
        cache = new RulePackCache();
    }

    @Test
    void latestCache_secondCallSkipsLoader() {
        AtomicInteger loads = new AtomicInteger();
        RulePackDto dto = dto("id-1");

        Optional<RulePackDto> first  = cache.getOrLoad("US", 2025, () -> { loads.incrementAndGet(); return dto; });
        Optional<RulePackDto> second = cache.getOrLoad("US", 2025, () -> { loads.incrementAndGet(); return dto; });

        assertTrue(first.isPresent());
        assertTrue(second.isPresent());
        assertEquals(1, loads.get(), "Loader should only be invoked on the first call");
    }

    @Test
    void latestCache_loaderReturningNullIsNotCached() {
        AtomicInteger loads = new AtomicInteger();

        Optional<RulePackDto> first  = cache.getOrLoad("US", 2099, () -> { loads.incrementAndGet(); return null; });
        Optional<RulePackDto> second = cache.getOrLoad("US", 2099, () -> { loads.incrementAndGet(); return null; });

        assertFalse(first.isPresent());
        assertFalse(second.isPresent());
        assertEquals(2, loads.get(), "Loader must re-run when previous result was null");
    }

    @Test
    void payloadCache_secondCallSkipsLoader() {
        AtomicInteger loads = new AtomicInteger();
        Map<String, Object> payload = Map.of("country", "US", "taxYear", 2025);

        Optional<Map<String, Object>> first  = cache.getOrLoadPayload("id-1",
                () -> { loads.incrementAndGet(); return Optional.of(payload); });
        Optional<Map<String, Object>> second = cache.getOrLoadPayload("id-1",
                () -> { loads.incrementAndGet(); return Optional.of(payload); });

        assertTrue(first.isPresent());
        assertTrue(second.isPresent());
        assertEquals(1, loads.get(), "Payload loader should only be invoked once per id");
    }

    @Test
    void payloadCache_loaderReturningEmptyIsNotCached() {
        AtomicInteger loads = new AtomicInteger();

        Optional<Map<String, Object>> first  = cache.getOrLoadPayload("missing",
                () -> { loads.incrementAndGet(); return Optional.empty(); });
        Optional<Map<String, Object>> second = cache.getOrLoadPayload("missing",
                () -> { loads.incrementAndGet(); return Optional.empty(); });

        assertFalse(first.isPresent());
        assertFalse(second.isPresent());
        assertEquals(2, loads.get(), "Payload loader must re-run when previous result was empty");
    }

    @Test
    void invalidateAll_clearsBothCaches() {
        AtomicInteger latestLoads  = new AtomicInteger();
        AtomicInteger payloadLoads = new AtomicInteger();

        cache.getOrLoad("US", 2025, () -> { latestLoads.incrementAndGet(); return dto("id-1"); });
        cache.getOrLoadPayload("id-1",
                () -> { payloadLoads.incrementAndGet(); return Optional.of(Map.of("k", "v")); });

        cache.invalidateAll();

        cache.getOrLoad("US", 2025, () -> { latestLoads.incrementAndGet(); return dto("id-1"); });
        cache.getOrLoadPayload("id-1",
                () -> { payloadLoads.incrementAndGet(); return Optional.of(Map.of("k", "v")); });

        assertEquals(2, latestLoads.get());
        assertEquals(2, payloadLoads.get());
    }

    private static RulePackDto dto(String id) {
        return RulePackDto.builder()
                .id(id)
                .country("US")
                .taxYear(2025)
                .version("US-2025.1.0")
                .status(RulePackStatus.PUBLISHED)
                .build();
    }
}
