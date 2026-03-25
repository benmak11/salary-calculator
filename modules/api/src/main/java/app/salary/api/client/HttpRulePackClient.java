package app.salary.api.client;

import app.salary.calculator.client.RulePackClient;
import app.salary.rules.RulePack;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

public class HttpRulePackClient implements RulePackClient {

    private static final Logger log = LoggerFactory.getLogger(HttpRulePackClient.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public HttpRulePackClient(RestTemplate restTemplate, ObjectMapper objectMapper, String baseUrl) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<RulePack> fetchLatest(String country, int taxYear) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return Optional.empty();
        }
        try {
            String latestUrl = baseUrl + "/v1/rule-packs/latest?country={country}&taxYear={taxYear}";
            Map<String, Object> metadata = restTemplate.getForObject(latestUrl, Map.class, country, taxYear);
            if (metadata == null) {
                return Optional.empty();
            }

            String id = (String) metadata.get("id");
            String downloadUrl = baseUrl + "/v1/rule-packs/{id}/download";
            Map<String, Object> rulePackJson = restTemplate.getForObject(downloadUrl, Map.class, id);
            if (rulePackJson == null) {
                return Optional.empty();
            }

            RulePack rulePack = objectMapper.convertValue(rulePackJson, RulePack.class);
            log.debug("Fetched rule pack from rule-pack-service: {} {}", country, taxYear);
            return Optional.of(rulePack);
        } catch (Exception e) {
            log.warn("Failed to fetch rule pack from rule-pack-service for {} {}: {}", country, taxYear, e.getMessage());
            return Optional.empty();
        }
    }
}
