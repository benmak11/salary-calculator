package app.salary.api.ratelimit;

import app.salary.api.auth.AuthMiddleware;
import app.salary.common.constants.ApiConstants;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

/**
 * Throttles callers before a request reaches a controller.
 *
 * <p>Runs <em>after</em> {@link AuthMiddleware} so a signed-in caller is keyed on their
 * userId and gets their own budget regardless of which network they are on. Anonymous
 * callers fall back to client IP, which is coarse: everyone behind one NAT shares a bucket.
 * That is the accepted cost of throttling traffic that has no identity attached.
 *
 * <p>Health and metrics endpoints are exempt — Cloud Run's probes and the Prometheus
 * scraper are not the traffic this protects against, and throttling them turns a load spike
 * into a failed health check.
 */
public class RateLimitMiddleware {
    private static final Logger log = LoggerFactory.getLogger(RateLimitMiddleware.class);
    private static final String EVENTS_PATH = "/v1/events";
    private static final String RETRY_AFTER = "Retry-After";

    private final RateLimiter defaultLimiter;
    private final RateLimiter eventsLimiter;

    public RateLimitMiddleware(RateLimiter defaultLimiter, RateLimiter eventsLimiter) {
        this.defaultLimiter = defaultLimiter;
        this.eventsLimiter = eventsLimiter;
    }

    public void handle(Context ctx) {
        String path = ctx.path();
        if (path.startsWith("/actuator")) {
            return;
        }

        RateLimiter limiter = EVENTS_PATH.equals(path) ? eventsLimiter : defaultLimiter;
        String key = callerKey(ctx);
        if (limiter.tryAcquire(key)) {
            return;
        }

        // Never log the key itself: for anonymous callers it is an IP address.
        log.warn("rate limit exceeded: path={} authenticated={}",
                path, AuthMiddleware.currentUserId(ctx).isPresent());
        ctx.header(RETRY_AFTER, "60");
        throw new io.javalin.http.HttpResponseException(
                HttpStatus.TOO_MANY_REQUESTS.getCode(),
                "Too many requests",
                Map.of(ApiConstants.ERROR, "Too many requests. Retry in a minute."));
    }

    private static String callerKey(Context ctx) {
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
