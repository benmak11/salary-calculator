package app.salary.api.controller;

import app.salary.api.auth.AuthMiddleware;
import app.salary.api.auth.SessionTokenService;
import app.salary.api.client.StockClient;
import app.salary.api.client.StockLookupException;
import app.salary.common.dto.StockQuote;
import app.salary.common.dto.StockSymbol;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class StocksControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SessionTokenService sessionTokens;

    @BeforeEach
    void setUp() {
        byte[] secret = new byte[32];
        for (int i = 0; i < secret.length; i++) secret[i] = (byte) i;
        sessionTokens = new SessionTokenService(secret);
    }

    /** Happy-path fake: one search hit, quotes only for AAPL. */
    private static final StockClient FAKE_CLIENT = new StockClient() {
        @Override
        public List<StockSymbol> search(String query) {
            return List.of(new StockSymbol("AAPL", "Apple Inc"));
        }

        @Override
        public Optional<StockQuote> quote(String symbol) {
            return "AAPL".equalsIgnoreCase(symbol)
                    ? Optional.of(new StockQuote("AAPL", 232.14, "2026-07-16T14:30:00Z"))
                    : Optional.empty();
        }
    };

    private static final StockClient FAILING_CLIENT = new StockClient() {
        @Override
        public List<StockSymbol> search(String query) {
            throw new StockLookupException("provider down");
        }

        @Override
        public Optional<StockQuote> quote(String symbol) {
            throw new StockLookupException("provider down");
        }
    };

    private Javalin app(StockClient client) {
        AuthMiddleware middleware = new AuthMiddleware(sessionTokens);
        return Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(MAPPER, false));
            config.startup.showJavalinBanner = false;
            config.routes.before(middleware::handle);
            new StocksController(client).register(config.routes);
        });
    }

    private String bearer() {
        return "Bearer " + sessionTokens.mint("user-1").token();
    }

    @Test
    void bothEndpoints_anonymous_shouldReturn401() {
        JavalinTest.test(app(FAKE_CLIENT), (server, client) -> {
            assertEquals(401, client.get("/v1/stocks/search?q=apple").code());
            assertEquals(401, client.get("/v1/stocks/quote/AAPL").code());
        });
    }

    @Test
    void search_returnsItems() {
        JavalinTest.test(app(FAKE_CLIENT), (server, client) -> {
            var resp = client.get("/v1/stocks/search?q=apple", r -> r.header("Authorization", bearer()));
            assertEquals(200, resp.code());
            JsonNode items = MAPPER.readTree(resp.body().string()).get("items");
            assertEquals(1, items.size());
            assertEquals("AAPL", items.get(0).get("symbol").asText());
            assertEquals("Apple Inc", items.get(0).get("name").asText());
        });
    }

    @Test
    void search_withoutQuery_returns400() {
        JavalinTest.test(app(FAKE_CLIENT), (server, client) -> {
            assertEquals(400, client.get("/v1/stocks/search", r -> r.header("Authorization", bearer())).code());
            assertEquals(400, client.get("/v1/stocks/search?q=", r -> r.header("Authorization", bearer())).code());
        });
    }

    @Test
    void quote_knownSymbol_returnsPrice() {
        JavalinTest.test(app(FAKE_CLIENT), (server, client) -> {
            var resp = client.get("/v1/stocks/quote/AAPL", r -> r.header("Authorization", bearer()));
            assertEquals(200, resp.code());
            JsonNode body = MAPPER.readTree(resp.body().string());
            assertEquals(232.14, body.get("price").asDouble(), 0.001);
            assertEquals("2026-07-16T14:30:00Z", body.get("asOf").asText());
        });
    }

    @Test
    void quote_unknownSymbol_returns404() {
        JavalinTest.test(app(FAKE_CLIENT), (server, client) -> {
            assertEquals(404, client.get("/v1/stocks/quote/ZZZZZZ", r -> r.header("Authorization", bearer())).code());
        });
    }

    @Test
    void noProviderConfigured_returns503() {
        JavalinTest.test(app(null), (server, client) -> {
            var resp = client.get("/v1/stocks/search?q=apple", r -> r.header("Authorization", bearer()));
            assertEquals(503, resp.code());
            assertTrue(resp.body().string().contains("Stock lookup unavailable"));
            assertEquals(503, client.get("/v1/stocks/quote/AAPL", r -> r.header("Authorization", bearer())).code());
        });
    }

    @Test
    void providerFailure_returns503() {
        JavalinTest.test(app(FAILING_CLIENT), (server, client) -> {
            assertEquals(503, client.get("/v1/stocks/search?q=apple", r -> r.header("Authorization", bearer())).code());
            assertEquals(503, client.get("/v1/stocks/quote/AAPL", r -> r.header("Authorization", bearer())).code());
        });
    }
}
