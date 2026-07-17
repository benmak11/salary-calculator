package app.salary.api.client;

import app.salary.common.constants.ApiConstants;
import app.salary.common.dto.StockQuote;
import app.salary.common.dto.StockSymbol;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Finnhub-backed {@link StockClient} (https://finnhub.io — free tier, 60 calls/min).
 *
 * The API key travels in the {@code X-Finnhub-Token} header, never in the URL, so it
 * can't leak through request logs. Caffeine caches keep quote traffic well under the
 * rate limit: symbol searches barely change (long TTL), quotes stay fresh (short TTL).
 * Upstream failures throw {@link StockLookupException}; unknown symbols are empty.
 */
public class FinnhubStockClient implements StockClient {

    private static final Logger log = LoggerFactory.getLogger(FinnhubStockClient.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration SEARCH_CACHE_TTL = Duration.ofHours(6);
    private static final Duration QUOTE_CACHE_TTL = Duration.ofSeconds(60);
    private static final int MAX_RESULTS = 10;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;
    private final Cache<String, List<StockSymbol>> searchCache;
    private final Cache<String, StockQuote> quoteCache;

    public FinnhubStockClient(HttpClient httpClient, ObjectMapper objectMapper,
                              String baseUrl, String apiKey) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.searchCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(SEARCH_CACHE_TTL)
                .build();
        this.quoteCache = Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(QUOTE_CACHE_TTL)
                .build();
    }

    @Override
    public List<StockSymbol> search(String query) {
        String key = query.trim().toLowerCase(Locale.ROOT);
        List<StockSymbol> cached = searchCache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        String url = baseUrl + "/search?q=" + URLEncoder.encode(query.trim(), StandardCharsets.UTF_8)
                + "&exchange=US";
        JsonNode root = getJson(url, "search");
        List<StockSymbol> results = new ArrayList<>();
        JsonNode items = root.path("result");
        for (JsonNode item : items) {
            if (results.size() >= MAX_RESULTS)
                break;
            String symbol = item.path("displaySymbol").asText(item.path("symbol").asText(""));
            String name = item.path("description").asText("");
            // Keep primary listings only (warrants, units, etc. carry a dot suffix)
            if (!symbol.isBlank() && !symbol.contains(".")) {
                results.add(new StockSymbol(symbol, titleCase(name)));
            }
        }
        searchCache.put(key, results);
        return results;
    }

    @Override
    public Optional<StockQuote> quote(String symbol) {
        String key = symbol.trim().toUpperCase(Locale.ROOT);
        StockQuote cached = quoteCache.getIfPresent(key);
        if (cached != null) {
            return Optional.of(cached);
        }
        String url = baseUrl + "/quote?symbol=" + URLEncoder.encode(key, StandardCharsets.UTF_8);
        JsonNode root = getJson(url, "quote");
        double current = root.path("c").asDouble(0.0);
        long tradeEpoch = root.path("t").asLong(0L);
        // Finnhub answers unknown symbols with an all-zero quote rather than an error
        if (current <= 0.0 && tradeEpoch == 0L) {
            return Optional.empty();
        }
        Instant asOf = tradeEpoch > 0 ? Instant.ofEpochSecond(tradeEpoch) : Instant.now();
        StockQuote stockQuote = new StockQuote(key, current, asOf.toString());
        quoteCache.put(key, stockQuote);
        return Optional.of(stockQuote);
    }

    private JsonNode getJson(String url, String operation) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("X-Finnhub-Token", apiKey);
        // Propagate the request correlation id on outbound calls (same convention as
        // HttpRulePackClient) so provider-side request logs can be tied back to ours.
        String requestId = MDC.get(ApiConstants.MDC_REQUEST_ID);
        if (requestId != null && !requestId.isBlank()) {
            builder.header("X-Request-Id", requestId);
        }
        HttpRequest request = builder.GET().build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2 || response.body() == null || response.body().isBlank()) {
                log.warn("Finnhub {} returned status {}", operation, response.statusCode());
                throw new StockLookupException("Stock provider returned status " + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StockLookupException("Stock lookup interrupted", e);
        } catch (IOException e) {
            log.warn("Finnhub {} failed: {}", operation, e.getMessage());
            throw new StockLookupException("Stock provider unreachable", e);
        }
    }

    /** Finnhub descriptions arrive ALL-CAPS ("APPLE INC"); soften for display. */
    private static String titleCase(String name) {
        if (name.isBlank()) return name;
        String[] words = name.toLowerCase(Locale.ROOT).split(" ");
        StringBuilder sb = new StringBuilder(name.length());
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }
}
