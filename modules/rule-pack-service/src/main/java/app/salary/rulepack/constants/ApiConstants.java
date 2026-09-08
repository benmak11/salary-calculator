package app.salary.rulepack.constants;

/**
 * JSON field / query-param names for the rule-pack API surface. Mirrors
 * {@code app.salary.common.constants.ApiConstants} — this service deliberately
 * does not depend on modules/common, so it keeps its own copy.
 */
public final class ApiConstants {

    public static final String ERROR = "error";
    public static final String COUNTRY = "country";
    public static final String TAX_YEAR = "taxYear";

    // Request-logging MDC keys and the timing attribute the access log reads back
    public static final String MDC_DURATION_MS = "duration_ms";
    public static final String MDC_METHOD = "method";
    public static final String MDC_PATH = "path";
    public static final String ATTR_START_NANOS = "_start_nanos";

    // HTTP headers and content types
    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    public static final String CONTENT_TYPE_JSON = "application/json";
    public static final String CONTENT_TYPE_PROMETHEUS = "text/plain; version=0.0.4";

    // Actuator surface
    public static final String PATH_HEALTH = "/actuator/health";
    public static final String PATH_PROMETHEUS = "/actuator/prometheus";
    public static final String STATUS = "status";
    public static final String STATUS_UP = "UP";

    // Error messages returned to clients. Centralised so a wording change cannot leave
    // one endpoint saying something different from the rest for the same condition.
    public static final String ERROR_INTERNAL = "Internal server error";

    private ApiConstants() {
    }
}
