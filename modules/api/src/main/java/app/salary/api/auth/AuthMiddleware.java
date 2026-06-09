package app.salary.api.auth;

import io.javalin.http.Context;
import org.slf4j.MDC;

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
    private static final String MDC_USER_ID = "user_id";

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
            MDC.put(MDC_USER_ID, id);
        });
    }

    /** Pulls the user id off the context, if the request was authenticated. */
    public static Optional<String> currentUserId(Context ctx) {
        Object v = ctx.attribute(ATTR_USER_ID);
        return v == null ? Optional.empty() : Optional.of(v.toString());
    }
}
