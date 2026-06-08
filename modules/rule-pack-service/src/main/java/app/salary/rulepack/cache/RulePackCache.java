package app.salary.rulepack.cache;

import app.salary.rulepack.dto.RulePackDto;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * In-process caches for rule-pack reads.
 *
 * <p>Two independent caches share this class:
 * <ul>
 *   <li><b>latest cache</b> — keyed by {@code country:taxYear}, holds the {@link RulePackDto}
 *       metadata returned by {@code findLatest}. Short TTL because new versions are
 *       published occasionally and consumers should pick them up promptly.</li>
 *   <li><b>payload cache</b> — keyed by rule-pack {@code id}, holds the downloaded JSON.
 *       Long TTL because a given id maps to an immutable storage blob; new versions
 *       always get new ids.</li>
 * </ul>
 *
 * <p>{@link #invalidateAll()} clears both — called from {@code RulePackService} on
 * publish/deprecate so newly-published packs are visible immediately.
 */
public class RulePackCache {

    private final Cache<String, RulePackDto> latestCache;
    private final Cache<String, Map<String, Object>> payloadCache;

    public RulePackCache() {
        this.latestCache = Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterWrite(Duration.ofMinutes(10))
                .build();
        this.payloadCache = Caffeine.newBuilder()
                .maximumSize(50)
                .expireAfterAccess(Duration.ofHours(1))
                .build();
    }

    public Optional<RulePackDto> getOrLoad(String country, Integer taxYear,
                                           Supplier<RulePackDto> loader) {
        String key = latestKey(country, taxYear);
        RulePackDto cached = latestCache.getIfPresent(key);
        if (cached != null) {
            return Optional.of(cached);
        }
        RulePackDto loaded = loader.get();
        if (loaded != null) {
            latestCache.put(key, loaded);
        }
        return Optional.ofNullable(loaded);
    }

    public Optional<Map<String, Object>> getOrLoadPayload(
            String id, Supplier<Optional<Map<String, Object>>> loader) {
        Map<String, Object> cached = payloadCache.getIfPresent(id);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<Map<String, Object>> loaded = loader.get();
        loaded.ifPresent(p -> payloadCache.put(id, p));
        return loaded;
    }

    public void invalidateAll() {
        latestCache.invalidateAll();
        payloadCache.invalidateAll();
    }

    private static String latestKey(String country, Integer taxYear) {
        return country + ":" + taxYear;
    }
}
