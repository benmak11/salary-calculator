package app.salary.api.ratelimit;

import app.salary.api.auth.AuthMiddleware;
import io.javalin.http.Context;

import java.util.Optional;

/**
 * Derives the bucket a request is counted against.
 *
 * <p>Shared by the blanket middleware and by routes that carry their own limiter, so a
 * signed-in caller is keyed the same way wherever the throttle happens to live.
 *
 * <p>Never log a key: for anonymous callers it is an IP address.
 */
public final class CallerKey {

    private CallerKey() {}

    /**
     * A signed-in caller is keyed on their userId, so they get their own budget regardless of
     * which network they are on. Anonymous callers fall back to client IP, which is coarse:
     * everyone behind one NAT shares a bucket. That is the accepted cost of throttling traffic
     * with no identity attached.
     */
    public static String of(Context ctx) {
        Optional<String> userId = AuthMiddleware.currentUserId(ctx);
        return userId.map(s -> "user:" + s).orElseGet(() -> "ip:" + clientIp(ctx));
    }

    /**
     * Cloud Run terminates TLS at its front end, so {@code ctx.ip()} is the proxy. The
     * left-most {@code X-Forwarded-For} entry is the original client.
     */
    private static String clientIp(Context ctx) {
        String forwarded = ctx.header("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            String first = (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        return ctx.ip();
    }
}
