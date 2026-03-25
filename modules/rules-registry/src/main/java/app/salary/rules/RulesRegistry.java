package app.salary.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

import java.io.IOException;
import java.io.InputStream;

public class RulesRegistry {
    private static final Logger log = LoggerFactory.getLogger(RulesRegistry.class);
    private final ObjectMapper objectMapper;

    public RulesRegistry() {
        this.objectMapper = new ObjectMapper();
    }

    @Cacheable(value = "rulePacks", key = "#country + '-' + #taxYear")
    public RulePack getRulePack(String country, int taxYear) {
        return loadRulePack(country, taxYear);
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
                throw new RuntimeException(errorMsg);
            }
            return objectMapper.readValue(is, RulePack.class);
        } catch (IOException io) {
            log.error("Failed to load rule pack: {}", fileName, io);
            throw new RuntimeException("Failed to load rule pack: " + fileName, io);
        }
    }

    @CacheEvict(value = "rulePacks", allEntries = true)
    public void clearCache() {
        log.info("Evicted all entries from rulePacks cache");
    }
}
