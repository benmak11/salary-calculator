package app.salary.api.client;

import app.salary.calculator.client.RulePackClient;
import app.salary.common.constants.ApiConstants;
import app.salary.rules.RulePack;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

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
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final int CACHE_MAX_SIZE = 50;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final Supplier<String> idTokenSupplier;
    private final Cache<String, RulePack> rulePackCache;

    public HttpRulePackClient(HttpClient httpClient, ObjectMapper objectMapper, String baseUrl) {
        this(httpClient, objectMapper, baseUrl, null);
    }

    public HttpRulePackClient(HttpClient httpClient,
                              ObjectMapper objectMapper,
                              String baseUrl,
                              Supplier<String> idTokenSupplier) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.idTokenSupplier = idTokenSupplier;
        this.rulePackCache = Caffeine.newBuilder()
                .maximumSize(CACHE_MAX_SIZE)
                .expireAfterWrite(CACHE_TTL)
                .build();
    }

    @Override
    public Optional<RulePack> fetchLatest(String country, int taxYear) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return Optional.empty();
        }
        String key = country + ":" + taxYear;
        RulePack cached = rulePackCache.getIfPresent(key);
        if (cached != null) {
            return Optional.of(cached);
        }
        RulePack fetched = doFetch(country, taxYear);
        if (fetched != null) {
            rulePackCache.put(key, fetched);
            return Optional.of(fetched);
        }
        // Caches misses are NOT stored — next call re-attempts the upstream so a
        // transient rule-pack-service outage doesn't pin clients to the classpath
        // fallback for the whole TTL window.
        return Optional.empty();
    }

    private RulePack doFetch(String country, int taxYear) {
        try {
            String latestUrl = String.format("%s/v1/rule-packs/latest?country=%s&taxYear=%d",
                    trimTrailingSlash(baseUrl),
                    URLEncoder.encode(country, StandardCharsets.UTF_8),
                    taxYear);
            Map<String, Object> metadata = getJson(latestUrl);
            if (metadata.isEmpty()) {
                return null;
            }

            String id = (String) metadata.get("id");
            if (id == null || id.isBlank()) {
                return null;
            }

            String downloadUrl = String.format("%s/v1/rule-packs/%s/download",
                    trimTrailingSlash(baseUrl),
                    URLEncoder.encode(id, StandardCharsets.UTF_8));
            // Empty map = HTTP failure or blank body. Bail so the orchestrator falls back
            // to the embedded classpath rules instead of caching an all-null RulePack.
            Map<String, Object> rulePackJson = getJson(downloadUrl);
            if (rulePackJson.isEmpty()) {
                return null;
            }

            RulePack rulePack = objectMapper.convertValue(rulePackJson, RulePack.class);
            log.debug("Fetched rule pack from rule-pack-service: {} {}", country, taxYear);
            return rulePack;
        } catch (Exception e) {
            log.warn("Failed to fetch rule pack from rule-pack-service for {} {}: {}", country, taxYear, e.getMessage());
            return null;
        }
    }

    private Map<String, Object> getJson(String url) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json");
        String requestId = MDC.get(ApiConstants.MDC_REQUEST_ID);
        if (requestId != null && !requestId.isBlank()) {
            builder.header("X-Request-Id", requestId);
        }
        if (idTokenSupplier != null) {
            builder.header("Authorization", "Bearer " + idTokenSupplier.get());
        }
        HttpRequest req = builder.GET().build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2 || resp.body() == null || resp.body().isBlank()) {
            return new HashMap<>();
        }
        return objectMapper.readValue(resp.body(), JSON_MAP);
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
