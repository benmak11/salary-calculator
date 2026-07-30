package app.salary.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads rule packs from the classpath and caches them in-process.
 *
 * Previously used Spring's {@code @Cacheable} which only worked through a Spring proxy.
 * After the Javalin migration this uses Caffeine directly — caching now works regardless
 * of how the registry is instantiated (Spring bean, plain {@code new}, or unit test).
 */
public class RulesRegistry {
    private static final Logger log = LoggerFactory.getLogger(RulesRegistry.class);

    // How far back/forward from the current calendar year to probe the classpath for
    // embedded rule packs. Widened here (not in the caller) if a new tax year ships.
    private static final int PROBE_YEARS_BACK = 10;
    private static final int PROBE_YEARS_FORWARD = 1;

    private final ObjectMapper objectMapper;
    private final Cache<String, RulePack> cache;

    public RulesRegistry() {
        this.objectMapper = new ObjectMapper();
        this.cache = Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(Duration.ofHours(1))
                .build();
    }

    public RulePack getRulePack(String country, int taxYear) {
        String key = country + "-" + taxYear;
        return cache.get(key, k -> loadRulePack(country, taxYear));
    }

    /**
     * Tax years with an embedded rule pack for {@code country}, newest first. Derived by
     * probing the same classpath convention {@link #loadRulePack} uses ({@code
     * /rulepacks/{country}-{year}.json}) across a bounded window around the current
     * calendar year, rather than a hardcoded literal — this is the actual source of truth
     * for "which years are supported" absent a live rule-pack-service listing endpoint.
     */
    public List<Integer> getSupportedTaxYears(String country) {
        int currentYear = Year.now(ZoneOffset.UTC).getValue();
        List<Integer> years = new ArrayList<>();
        for (int year = currentYear + PROBE_YEARS_FORWARD; year >= currentYear - PROBE_YEARS_BACK; year--) {
            if (hasRulePack(country, year)) {
                years.add(year);
            }
        }
        return List.copyOf(years);
    }

    private boolean hasRulePack(String country, int taxYear) {
        String fileName = String.format("/rulepacks/%s-%d.json", country, taxYear);
        try (InputStream is = getClass().getResourceAsStream(fileName)) {
            return is != null;
        } catch (IOException io) {
            return false;
        }
    }

    private RulePack loadRulePack(String country, int taxYear) {
        String fileName = String.format("/rulepacks/%s-%d.json", country, taxYear);
        log.info("Loading rule pack from classpath: {}", fileName);

        try (InputStream is = getClass().getResourceAsStream(fileName)) {
            if (is == null) {
                String errorMsg = String.format(
                    "Rule pack not found: %s. Please ensure tax year %d is supported for country %s. " +
                    "Available rule packs should be placed in src/main/resources/rulepacks/",
                    fileName, taxYear, country
                );
                log.error(errorMsg);
                throw new RulePackLoadException(errorMsg);
            }
            return objectMapper.readValue(is, RulePack.class);
        } catch (IOException io) {
            log.error("Failed to load rule pack: {}", fileName, io);
            throw new RulePackLoadException("Failed to load rule pack: " + fileName, io);
        }
    }

    /** Clear the rule-pack cache. Invoked from a future Pub/Sub subscriber on RULE_PACK_PUBLISHED events. */
    public void clearCache() {
        cache.invalidateAll();
        log.info("Evicted all entries from rule pack cache");
    }
}
