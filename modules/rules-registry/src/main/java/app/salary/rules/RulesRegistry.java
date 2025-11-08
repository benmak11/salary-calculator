package app.salary.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

public class RulesRegistry {
    private static final Logger log = LoggerFactory.getLogger(RulesRegistry.class);
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
        String key = String.format("%s-%d", country, taxYear);
        return cache.get(key, k -> loadRulePack(country, taxYear));
    }

    private RulePack loadRulePack(String country, int taxYear) {
        String fileName = String.format("/rulepacks/%s-%d.json", country, taxYear);
        log.info("Loading rule pack: {}", fileName);

        try (InputStream is = getClass().getResourceAsStream(fileName)) {
            if (is == null) {
                String errorMsg = String.format(
                    "Rule pack not found: %s. Please ensure tax year %d is supported for country %s. " +
                    "Available rule packs should be placed in src/main/resources/rulepacks/",
                    fileName, taxYear, country
                );
                log.error(errorMsg);
                throw new RuntimeException(errorMsg);
            }
            return objectMapper.readValue(is, RulePack.class);
        } catch (IOException io) {
            log.error("Failed to load rule pack: {}", fileName, io);
            throw new RuntimeException("Failed to load rule pack: " + fileName, io);
        }
    }

    public void clearCache() {
        cache.invalidateAll();
    }
}
