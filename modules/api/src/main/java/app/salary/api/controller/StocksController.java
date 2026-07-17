package app.salary.api.controller;

import app.salary.api.auth.AuthMiddleware;
import app.salary.api.client.StockClient;
import app.salary.api.client.StockLookupException;
import app.salary.common.constants.ApiConstants;
import app.salary.common.dto.StockQuote;
import app.salary.common.dto.StockSearchResponse;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Map;
import java.util.Optional;

/**
 * Ticker search + live quotes, proxied through the backend so the provider API key
 * never ships in the app. Auth-required (the add-grant flow is signed-in only) —
 * this also keeps the key from being farmable through an anonymous endpoint.
 * When the provider is unconfigured or down, both endpoints answer 503 and the
 * client flips to manual price entry.
 */
public class StocksController {
    private static final Logger log = LoggerFactory.getLogger(StocksController.class);

    private final StockClient stockClient;

    /** {@code stockClient} may be null when no provider API key is configured. */
    public StocksController(StockClient stockClient) {
        this.stockClient = stockClient;
    }

    public void register(RoutesConfig routes) {
        routes.get("/v1/stocks/search", this::search);
        routes.get("/v1/stocks/quote/{symbol}", this::quote);
    }

    private void search(Context ctx) {
        Optional<String> userId = requireUser(ctx);
        if (userId.isEmpty()) return;
        if (unavailable(ctx)) return;
        String query = ctx.queryParam("q");
        if (query == null || query.isBlank()) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of(ApiConstants.ERROR, "Query parameter q is required"));
            return;
        }
        try {
            StockSearchResponse response = new StockSearchResponse(stockClient.search(query));
            log.info("stocks search: results={}", response.getItems().size());
            ctx.json(response);
        } catch (StockLookupException e) {
            providerDown(ctx, e);
        }
    }

    private void quote(Context ctx) {
        Optional<String> userId = requireUser(ctx);
        if (userId.isEmpty()) return;
        if (unavailable(ctx)) return;
        String symbol = ctx.pathParam("symbol");
        try {
            Optional<StockQuote> stockQuote = stockClient.quote(symbol);
            if (stockQuote.isEmpty()) {
                ctx.status(HttpStatus.NOT_FOUND).json(Map.of(ApiConstants.ERROR, "Unknown symbol"));
                return;
            }
            log.info("stocks quote: symbol={}", stockQuote.get().getSymbol());
            ctx.json(stockQuote.get());
        } catch (StockLookupException e) {
            providerDown(ctx, e);
        }
    }

    private boolean unavailable(Context ctx) {
        if (stockClient != null) return false;
        ctx.status(HttpStatus.SERVICE_UNAVAILABLE)
                .json(Map.of(ApiConstants.ERROR, "Stock lookup unavailable"));
        return true;
    }

    private void providerDown(Context ctx, StockLookupException e) {
        log.warn("stock provider failure: {}", e.getMessage());
        ctx.status(HttpStatus.SERVICE_UNAVAILABLE)
                .json(Map.of(ApiConstants.ERROR, "Stock lookup unavailable"));
    }

    private Optional<String> requireUser(Context ctx) {
        Optional<String> userId = AuthMiddleware.currentUserId(ctx);
        if (userId.isEmpty()) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of(ApiConstants.ERROR, "Authentication required"));
            return Optional.empty();
        }
        MDC.put(ApiConstants.MDC_USER_ID, userId.get());
        return userId;
    }
}
