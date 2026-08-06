package app.salary.api.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Token bucket keyed by caller, with lazy refill.
 *
 * <p><b>Per-instance, not global.</b> Cloud Run runs several containers behind one URL and
 * this holds its buckets in memory, so the effective ceiling is roughly
 * {@code limit × instance count}. That is deliberate for a first cut: it bounds the work any
 * single container will accept, which is what protects the service at peak, and it costs no
 * extra round trip on every request. A globally exact limit needs shared state (Redis, or a
 * Firestore counter) or an edge policy in Cloud Armor — both are worth it only once the
 * approximate ceiling proves too loose.
 *
 * <p>Buckets expire after an idle window so the map cannot grow without bound from
 * one-shot callers.
 */
public class RateLimiter {
    private final Cache<String, Bucket> buckets;
    private final long capacity;
    private final double refillPerNano;
    private final LongSupplier nanoTime;

    /**
     * @param permitsPerMinute sustained rate
     * @param burst            how many requests may arrive at once before throttling starts
     */
    public RateLimiter(int permitsPerMinute, int burst, long maxTrackedCallers) {
        this(permitsPerMinute, burst, maxTrackedCallers, System::nanoTime);
    }

    /**
     * Test seam. Refill is a function of elapsed nanos, so handing tests a controllable
     * clock lets them assert the refill and ceiling behaviour outright instead of sleeping
     * and hoping — which is both slow and flaky on a loaded CI box.
     */
    RateLimiter(int permitsPerMinute, int burst, long maxTrackedCallers, LongSupplier nanoTime) {
        this.capacity = burst;
        this.refillPerNano = permitsPerMinute / 60_000_000_000.0;
        this.nanoTime = nanoTime;
        this.buckets = Caffeine.newBuilder()
                .maximumSize(maxTrackedCallers)
                .expireAfterAccess(Duration.ofMinutes(10))
                .build();
    }

    /** Consumes one permit for this caller; false means the caller is over their limit. */
    public boolean tryAcquire(String key) {
        return buckets.get(key, k -> new Bucket(capacity, nanoTime.getAsLong()))
                .tryConsume(refillPerNano, capacity, nanoTime.getAsLong());
    }

    private static final class Bucket {
        /** Tokens scaled by 1e6 so refill fractions survive integer arithmetic. */
        private static final long SCALE = 1_000_000L;

        private final AtomicLong tokens;
        private final AtomicLong lastRefillNanos;

        Bucket(long capacity, long now) {
            this.tokens = new AtomicLong(capacity * SCALE);
            this.lastRefillNanos = new AtomicLong(now);
        }

        boolean tryConsume(double refillPerNano, long capacity, long now) {
            long last = lastRefillNanos.getAndSet(now);
            long elapsed = Math.max(0, now - last);

            long refill = (long) (elapsed * refillPerNano * SCALE);
            long ceiling = capacity * SCALE;
            tokens.updateAndGet(current -> Math.min(ceiling, current + refill));

            return tokens.getAndUpdate(current -> current >= SCALE ? current - SCALE : current) >= SCALE;
        }
    }
}
