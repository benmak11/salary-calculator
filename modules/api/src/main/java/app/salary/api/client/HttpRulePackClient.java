package app.salary.api.client;

import app.salary.calculator.client.RulePackClient;
import app.salary.rules.RulePack;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Talks to {@code rule-pack-service} over HTTP. Previously used Spring's
 * {@code RestTemplate}; after the Javalin migration this uses the JDK's
 * {@link HttpClient} so the api module no longer depends on Spring at all.
 *
 * Behavior is unchanged: any HTTP failure or empty body returns
 * {@link Optional#empty()}, which causes the orchestrator to fall back to the
 * embedded classpath rule pack.
 */
public class HttpRulePackClient implements RulePackClient {

    private static final Logger log = LoggerFactory.getLogger(HttpRulePackClient.class);
    private static final TypeReference<Map<String, Object>> JSON_MAP = new TypeReference<>() {};
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public HttpRulePackClient(HttpClient httpClient, ObjectMapper objectMapper, String baseUrl) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
    }

    @Override
    public Optional<RulePack> fetchLatest(String country, int taxYear) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return Optional.empty();
        }
        try {
            String latestUrl = String.format("%s/v1/rule-packs/latest?country=%s&taxYear=%d",
                    trimTrailingSlash(baseUrl),
                    URLEncoder.encode(country, StandardCharsets.UTF_8),
                    taxYear);
            Map<String, Object> metadata = getJson(latestUrl);
            if (metadata == null) {
                return Optional.empty();
            }

            String id = (String) metadata.get("id");
            if (id == null || id.isBlank()) {
                return Optional.empty();
            }

            String downloadUrl = String.format("%s/v1/rule-packs/%s/download",
                    trimTrailingSlash(baseUrl),
                    URLEncoder.encode(id, StandardCharsets.UTF_8));
            Map<String, Object> rulePackJson = getJson(downloadUrl);
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

    private Map<String, Object> getJson(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2 || resp.body() == null || resp.body().isBlank()) {
            return null;
        }
        return objectMapper.readValue(resp.body(), JSON_MAP);
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
