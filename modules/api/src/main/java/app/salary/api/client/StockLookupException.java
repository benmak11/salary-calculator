package app.salary.api.client;

/** Upstream stock-data provider failed or was unreachable. Mapped to 503 at the edge. */
public class StockLookupException extends RuntimeException {
    public StockLookupException(String message) {
        super(message);
    }

    public StockLookupException(String message, Throwable cause) {
        super(message, cause);
    }
}
