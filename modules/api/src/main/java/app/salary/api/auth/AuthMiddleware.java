package app.salary.api.auth;

import app.salary.common.constants.ApiConstants;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.slf4j.MDC;

import java.util.Map;
import java.util.Optional;

/**
 * Reads a Bearer token from the {@code Authorization} header and, if valid, attaches
 * the user id to the request context as {@link #ATTR_USER_ID}. Missing or invalid
 * tokens are silently ignored — endpoints that require auth check for the attribute
 * themselves and return 401 when absent. This keeps {@code /v1/calculate} usable for
 * anonymous callers per the product requirements.
 */
public class AuthMiddleware {
    public static final String ATTR_USER_ID = "userId";
    private static final String BEARER_PREFIX = "Bearer ";

    private final SessionTokenService sessionTokens;

    public AuthMiddleware(SessionTokenService sessionTokens) {
        this.sessionTokens = sessionTokens;
    }

    public void handle(Context ctx) {
        String header = ctx.header("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) return;
        String token = header.substring(BEARER_PREFIX.length()).trim();
        Optional<String> userId = sessionTokens.validate(token);
        userId.ifPresent(id -> {
            ctx.attribute(ATTR_USER_ID, id);
            MDC.put(ApiConstants.MDC_USER_ID, id);
        });
    }

    /** Pulls the user id off the context, if the request was authenticated. */
    public static Optional<String> currentUserId(Context ctx) {
        Object v = ctx.attribute(ATTR_USER_ID);
        return v == null ? Optional.empty() : Optional.of(v.toString());
    }

    /**
     * Enforces auth for endpoints that require it: on success, MDCs the user id and
     * returns it; on failure, writes the 401 response body and returns empty. Callers
     * return immediately when the result is empty.
     */
    public static Optional<String> requireUser(Context ctx) {
        Optional<String> userId = currentUserId(ctx);
        if (userId.isEmpty()) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of(ApiConstants.ERROR, "Authentication required"));
            return Optional.empty();
        }
        MDC.put(ApiConstants.MDC_USER_ID, userId.get());
        return userId;
    }
}
