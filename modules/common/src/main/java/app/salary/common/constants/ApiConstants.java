package app.salary.common.constants;

/**
 * Shared JSON field / query-param names and MDC log keys used across the api
 * and calculator modules. rule-pack-service keeps its own copy (it deliberately
 * does not depend on this module) — keep the two in sync by name.
 */
public final class ApiConstants {

    // JSON response / query-param keys
    public static final String ERROR = "error";
    public static final String COUNTRY = "country";
    public static final String TAX_YEAR = "taxYear";

    // MDC log keys
    public static final String MDC_USER_ID = "user_id";
    public static final String MDC_REQUEST_ID = "request_id";

    private ApiConstants() {
    }
}
