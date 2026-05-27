package app.salary.rulepack.cache;

import app.salary.rulepack.dto.RulePackDto;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * In-process cache for "latest published rule pack" lookups.
 *
 * Replaces the previous Spring {@code @Cacheable("rulepack")} backed by Redis.
 * Local Docker deploys no longer require a separate Memorystore/Redis container —
 * each rule-pack-service instance keeps its own Caffeine cache. The TTL is short
 * enough that staleness on multi-instance deploys is bounded; the Pub/Sub fan-out
 * planned in the original CLAUDE.md notes will replace this with explicit eviction
 * once subscribers are wired up.
 */
public class RulePackCache {

    private final Cache<String, RulePackDto> cache;

    public RulePackCache() {
        this.cache = Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterWrite(Duration.ofMinutes(10))
                .build();
    }

    public Optional<RulePackDto> getOrLoad(String country, Integer taxYear,
                                           Supplier<RulePackDto> loader) {
        String key = key(country, taxYear);
        RulePackDto cached = cache.getIfPresent(key);
        if (cached != null) {
            return Optional.of(cached);
        }
        RulePackDto loaded = loader.get();
        if (loaded != null) {
            cache.put(key, loaded);
        }
        return Optional.ofNullable(loaded);
    }

    public void invalidateAll() {
        cache.invalidateAll();
    }

    private static String key(String country, Integer taxYear) {
        return country + ":" + taxYear;
    }
}
