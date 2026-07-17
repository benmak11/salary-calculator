package app.salary.api.client;

import app.salary.common.dto.StockQuote;
import app.salary.common.dto.StockSymbol;

import java.util.List;
import java.util.Optional;

/**
 * Live share-price lookups. {@link FinnhubStockClient} in prod; tests use fakes.
 * Implementations throw {@link StockLookupException} when the upstream is
 * unreachable — callers map that to 503 so the client falls back to manual
 * price entry (a designed-for, non-blocking state).
 */
public interface StockClient {

    /** Best-match ticker search, capped by the implementation. */
    List<StockSymbol> search(String query);

    /** Current quote; empty when the symbol is unknown. */
    Optional<StockQuote> quote(String symbol);
}
