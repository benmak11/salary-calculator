package app.salary.api.store;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link LinkCodeStore} for local runs and tests.
 *
 * <p>Takes an injected {@link Clock} so expiry can be tested without sleeping, and a
 * {@link SecureRandom} rather than {@code Random}: the whole security of a six-digit code
 * rests on it being unguessable within its ten minutes.
 */
public class InMemoryLinkCodeStore implements LinkCodeStore {

    /** Ten minutes, from the roadmap. Long enough to read aloud, short enough to bound guessing. */
    static final Duration TTL = Duration.ofMinutes(10);

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CODE_BOUND = 1_000_000;

    private final Map<String, LinkCode> byCode = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryLinkCodeStore() {
        this(Clock.systemUTC());
    }

    public InMemoryLinkCodeStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public LinkCode issue(String accountId) {
        deleteByAccountId(accountId);
        String code = generateCode();
        LinkCode issued = new LinkCode(code, accountId, clock.instant(),
                clock.instant().plus(TTL), 0, false);
        byCode.put(code, issued);
        return issued;
    }

    @Override
    public Optional<LinkCode> find(String code) {
        return Optional.ofNullable(byCode.get(code));
    }

    @Override
    public Optional<LinkCode> recordFailedAttempt(String code) {
        LinkCode updated = byCode.computeIfPresent(code, (k, existing) -> new LinkCode(
                existing.code(), existing.accountId(), existing.createdAt(),
                existing.expiresAt(), existing.attempts() + 1, existing.redeemed()));
        return Optional.ofNullable(updated);
    }

    @Override
    public boolean markRedeemed(String code) {
        LinkCode existing = byCode.get(code);
        if (existing == null || existing.redeemed()) {
            return false;
        }
        byCode.put(code, new LinkCode(existing.code(), existing.accountId(), existing.createdAt(),
                existing.expiresAt(), existing.attempts(), true));
        return true;
    }

    @Override
    public void deleteByAccountId(String accountId) {
        List<String> stale = byCode.entrySet().stream()
                .filter(e -> e.getValue().accountId().equals(accountId))
                .map(Map.Entry::getKey)
                .toList();
        stale.forEach(byCode::remove);
    }

    /** Zero-padded so every code is exactly six characters, including {@code 000042}. */
    static String generateCode() {
        return String.format("%06d", RANDOM.nextInt(CODE_BOUND));
    }
}
