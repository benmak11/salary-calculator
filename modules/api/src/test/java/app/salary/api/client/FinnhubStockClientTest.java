package app.salary.api.client;

import app.salary.common.constants.ApiConstants;
import app.salary.common.dto.StockQuote;
import app.salary.common.dto.StockSymbol;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinnhubStockClientTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private FinnhubStockClient client;

    @BeforeEach
    void setUp() {
        client = new FinnhubStockClient(httpClient, new ObjectMapper(),
                "https://finnhub.example/api/v1", "test-key");
    }

    @SuppressWarnings("unchecked")
    private void stubResponse(int status, String body) throws Exception {
        when(httpResponse.statusCode()).thenReturn(status);
        if (status / 100 == 2) {
            when(httpResponse.body()).thenReturn(body);
        }
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
    }

    // ── search ───────────────────────────────────────────────────────────────────

    @Test
    void search_mapsSymbolsAndTitleCasesNames() throws Exception {
        stubResponse(200, """
                {"count":3,"result":[
                  {"description":"APPLE INC","displaySymbol":"AAPL","symbol":"AAPL","type":"Common Stock"},
                  {"description":"APPLE HOSPITALITY REIT INC","displaySymbol":"APLE","symbol":"APLE","type":"Common Stock"},
                  {"description":"APPLE INC WARRANT","displaySymbol":"AAPL.W","symbol":"AAPL.W","type":"Warrant"}
                ]}""");

        List<StockSymbol> results = client.search("apple");

        assertEquals(2, results.size());
        assertEquals("AAPL", results.getFirst().getSymbol());
        assertEquals("Apple Inc", results.getFirst().getName());
        // dot-suffixed non-primary listings are filtered
        assertTrue(results.stream().noneMatch(s -> s.getSymbol().contains(".")));
    }

    @Test
    void search_cachesByNormalizedQuery() throws Exception {
        stubResponse(200, """
                {"count":1,"result":[{"description":"APPLE INC","displaySymbol":"AAPL","symbol":"AAPL"}]}""");

        client.search("Apple");
        client.search("  apple ");

        verify(httpClient, times(1)).send(any(HttpRequest.class), any());
    }

    @Test
    void search_non2xx_throwsStockLookupException() throws Exception {
        stubResponse(429, null);

        assertThrows(StockLookupException.class, () -> client.search("apple"));
    }

    @Test
    void search_ioFailure_throwsStockLookupException() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any())).thenThrow(new IOException("boom"));

        assertThrows(StockLookupException.class, () -> client.search("apple"));
    }

    @Test
    void search_sendsKeyInHeaderNotUrl() throws Exception {
        stubResponse(200, "{\"count\":0,\"result\":[]}");

        client.search("apple");

        verify(httpClient).send(argThat((HttpRequest req) ->
                "test-key".equals(req.headers().firstValue("X-Finnhub-Token").orElse(null))
                        && !req.uri().toString().contains("test-key")), any());
    }

    @Test
    void outboundCalls_propagateRequestIdFromMdc() throws Exception {
        stubResponse(200, "{\"count\":0,\"result\":[]}");
        MDC.put(ApiConstants.MDC_REQUEST_ID, "req-abc-123");
        try {
            client.search("apple");
        } finally {
            MDC.clear();
        }

        verify(httpClient).send(argThat((HttpRequest req) ->
                "req-abc-123".equals(req.headers().firstValue("X-Request-Id").orElse(null))), any());
    }

    @Test
    void outboundCalls_withoutRequestIdInMdc_omitHeader() throws Exception {
        stubResponse(200, "{\"count\":0,\"result\":[]}");
        MDC.clear();

        client.search("apple");

        verify(httpClient).send(argThat((HttpRequest req) ->
                req.headers().firstValue("X-Request-Id").isEmpty()), any());
    }

    // ── quote ────────────────────────────────────────────────────────────────────

    @Test
    void quote_mapsPriceAndTradeTime() throws Exception {
        stubResponse(200, """
                {"c":232.14,"d":1.2,"dp":0.52,"h":233.0,"l":230.1,"o":231.0,"pc":230.94,"t":1752674400}""");

        Optional<StockQuote> quote = client.quote("aapl");

        assertTrue(quote.isPresent());
        assertEquals("AAPL", quote.get().getSymbol());
        assertEquals(232.14, quote.get().getPrice(), 0.001);
        assertEquals("2025-07-16T14:00:00Z", quote.get().getAsOf());
    }

    @Test
    void quote_unknownSymbol_allZeroResponse_returnsEmpty() throws Exception {
        stubResponse(200, "{\"c\":0,\"d\":null,\"dp\":null,\"h\":0,\"l\":0,\"o\":0,\"pc\":0,\"t\":0}");

        assertTrue(client.quote("ZZZZZZ").isEmpty());
    }

    @Test
    void quote_cachesBySymbol() throws Exception {
        stubResponse(200, "{\"c\":232.14,\"t\":1752674400}");

        client.quote("AAPL");
        client.quote("aapl");

        verify(httpClient, times(1)).send(any(HttpRequest.class), any());
    }
}
