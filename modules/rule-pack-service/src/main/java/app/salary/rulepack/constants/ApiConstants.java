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

    private ApiConstants() {
    }
}
