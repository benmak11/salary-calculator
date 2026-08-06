package app.salary.api.ratelimit;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {

    /**
     * Refill is purely a function of elapsed nanos, so the tests drive a clock rather than
     * sleeping. Sleeping would make them slow, and flaky on a loaded CI box where the thread
     * can wake far later than asked.
     */
    private final AtomicLong clock = new AtomicLong(0);

    private RateLimiter limiter(int permitsPerMinute, int burst) {
        return new RateLimiter(permitsPerMinute, burst, 100, clock::get);
    }

    private void advanceMillis(long millis) {
        clock.addAndGet(millis * 1_000_000L);
    }

    @Test
    void allowsUpToTheBurstThenThrottles() {
        RateLimiter limiter = limiter(60, 5);
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire("caller"), "request " + (i + 1) + " should be allowed");
        }
        assertFalse(limiter.tryAcquire("caller"), "the burst is spent");
    }

    @Test
    void keepsCallersInSeparateBuckets() {
        RateLimiter limiter = limiter(60, 2);
        assertTrue(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"));
        // One caller exhausting their budget must not throttle anyone else.
        assertTrue(limiter.tryAcquire("b"));
    }

    @Test
    void refillsOverTime() {
        // 60000/min is one permit per millisecond.
        RateLimiter limiter = limiter(60_000, 2);
        assertTrue(limiter.tryAcquire("caller"));
        assertTrue(limiter.tryAcquire("caller"));
        assertFalse(limiter.tryAcquire("caller"));

        advanceMillis(1);
        assertTrue(limiter.tryAcquire("caller"), "a permit should be earned back after 1ms");
    }

    @Test
    void refillIsProportionalToElapsedTime() {
        RateLimiter limiter = limiter(60_000, 10);
        for (int i = 0; i < 10; i++) {
            assertTrue(limiter.tryAcquire("caller"));
        }
        assertFalse(limiter.tryAcquire("caller"));

        advanceMillis(3);
        assertTrue(limiter.tryAcquire("caller"));
        assertTrue(limiter.tryAcquire("caller"));
        assertTrue(limiter.tryAcquire("caller"));
        assertFalse(limiter.tryAcquire("caller"), "exactly three permits' worth of time passed");
    }

    @Test
    void doesNotRefillBeyondTheBurstCeiling() {
        RateLimiter limiter = limiter(60_000, 3);
        advanceMillis(1_000); // a thousand permits' worth of idle time

        assertTrue(limiter.tryAcquire("caller"));
        assertTrue(limiter.tryAcquire("caller"));
        assertTrue(limiter.tryAcquire("caller"));
        assertFalse(limiter.tryAcquire("caller"), "idling must not bank unlimited permits");
    }

    @Test
    void survivesConcurrentCallersWithoutOverGranting() throws InterruptedException {
        int burst = 20;
        // The clock never advances during this test, so nothing is earned back and the
        // burst is a hard ceiling. Racing threads must not push past it.
        RateLimiter limiter = limiter(60, burst);
        AtomicInteger granted = new AtomicInteger();

        int threads = 8;
        CountDownLatch latch = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            Thread.ofVirtual().start(() -> {
                try {
                    for (int j = 0; j < 20; j++) {
                        if (limiter.tryAcquire("shared")) granted.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();

        assertTrue(granted.get() <= burst, "granted " + granted.get() + " > burst " + burst);
        assertTrue(granted.get() > 0);
    }
}
