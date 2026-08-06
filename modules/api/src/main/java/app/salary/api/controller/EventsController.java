package app.salary.api.controller;

import app.salary.api.auth.AuthMiddleware;
import app.salary.api.store.AccountDirectory;
import app.salary.api.store.EventRecord;
import app.salary.api.store.EventStore;
import app.salary.api.store.Ulid;
import app.salary.api.validation.RequestValidator;
import app.salary.api.validation.ValidationException;
import app.salary.common.dto.AnalyticsEvent;
import app.salary.common.dto.EventBatchRequest;
import app.salary.common.dto.EventBatchResponse;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * First-party analytics ingest. <b>Deliberately open to anonymous callers.</b>
 *
 * <p>Sign-in is optional in the client, so a signed-in user is a self-selected, more
 * engaged minority. Phase 1's payday loop ships free to everyone and its exit criterion is
 * a session-frequency measurement; collecting that only from signed-in users would bias it
 * toward success, on exactly the decision that gates whether the paywall proceeds. The
 * pre-sign-in events — {@code session_start}, the onboarding steps, a signed-out user's
 * first {@code calculation_completed} — are the ones that make the measurement honest.
 *
 * <p>This briefly required auth (2026-08-05) to close an unauthenticated write path to
 * billed Firestore operations. That concern is now carried by
 * {@code app.salary.api.ratelimit}, which throttles anonymous callers by IP and caps a
 * batch at 50 events. Auth was the blunter instrument and cost more than it bought. If the
 * anonymous surface ever needs narrowing again, restrict it to an allowlist of the
 * pre-sign-in event names rather than closing the endpoint outright.
 *
 * <p>{@code deviceId} is on every batch, anonymous or not: it is what lets a signed-out
 * install be stitched to an account later, and it carries per-device rates (widget
 * installs, notification opt-in) that the account alone cannot express.
 *
 * <p>Event properties are never logged — only names and counts. The payload is exactly the
 * kind of thing the logging rules call a raw body.
 */
public class EventsController {
    private static final Logger log = LoggerFactory.getLogger(EventsController.class);

    /** Firestore's batch limit is 500; this leaves ample headroom and bounds an anonymous write. */
    private static final int MAX_PROPERTY_KEY_LENGTH = 40;
    private static final int MAX_PROPERTY_VALUE_LENGTH = 100;

    /**
     * Property keys whose values must be bucketed strings rather than numbers. A backstop
     * for the rule that raw amounts never reach the analytics store — the real contract is
     * client-side, since this cannot catch a raw figure hidden under a neutral key.
     */
    private static final Set<String> AMOUNT_KEY_TOKENS =
            Set.of("amount", "salary", "income", "gross_pay", "net_pay", "take_home", "balance");

    private final EventStore store;
    private final AccountDirectory accounts;
    private final RequestValidator validator;

    public EventsController(EventStore store, AccountDirectory accounts, RequestValidator validator) {
        this.store = store;
        this.accounts = accounts;
        this.validator = validator;
    }

    public void register(RoutesConfig routes) {
        routes.post("/v1/events", this::ingest);
    }

    private void ingest(Context ctx) {
        EventBatchRequest batch = ctx.bodyAsClass(EventBatchRequest.class);
        validator.validate(batch);

        String accountId = resolveAccountId(AuthMiddleware.currentUserId(ctx));
        Instant receivedAt = Instant.now();

        List<EventRecord> records = new ArrayList<>(batch.getEvents().size());
        for (int i = 0; i < batch.getEvents().size(); i++) {
            AnalyticsEvent event = batch.getEvents().get(i);
            records.add(new EventRecord(
                    Ulid.generate(),
                    event.getName(),
                    batch.getDeviceId(),
                    accountId,
                    batch.getClient(),
                    parseOccurredAt(event.getOccurredAt(), receivedAt),
                    receivedAt,
                    sanitizeProperties(event.getProperties(), i)));
        }

        int accepted = store.append(records);
        log.info("events ingested: count={} attributed={}", accepted, accountId != null);
        ctx.status(HttpStatus.ACCEPTED).json(new EventBatchResponse(accepted));
    }

    /**
     * Maps a session's provider sub onto an accountId, so events are keyed on the
     * identifier that survives the migration rather than on the sub itself.
     *
     * <p>Null is the common case, not an error: the sender may be signed out, or may have
     * last signed in before the identity schema shipped. Attribution falls back to the
     * batch's deviceId, which is what makes an anonymous run stitchable to an account once
     * that device signs in.
     */
    private String resolveAccountId(Optional<String> userId) {
        if (accounts == null || userId.isEmpty()) {
            return null;
        }
        return accounts.findAccountIdBySub(userId.get()).orElse(null);
    }

    /** Falls back to receive time rather than rejecting — a bad clock should not lose an event. */
    private static Instant parseOccurredAt(String raw, Instant fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException e) {
            return fallback;
        }
    }

    private static Map<String, Object> sanitizeProperties(Map<String, Object> properties, int index) {
        if (properties == null || properties.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> clean = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (key == null || key.isBlank() || value == null) {
                continue;
            }
            rejectIfUnbracketed(key, value, index);
            clean.put(truncate(key, MAX_PROPERTY_KEY_LENGTH), normalize(value));
        }
        return clean;
    }

    private static void rejectIfUnbracketed(String key, Object value, int index) {
        String field = "events[" + index + "].properties." + key;
        if (value instanceof Double || value instanceof Float) {
            // 2847.13 is a salary; a bucket is "2000-3000".
            throw new ValidationException(Map.of(field,
                    "decimal values are not accepted — send a bucketed string instead"));
        }
        String lowerKey = key.toLowerCase(Locale.ROOT);
        boolean amountish = AMOUNT_KEY_TOKENS.stream().anyMatch(lowerKey::contains);
        if (amountish && !(value instanceof String)) {
            throw new ValidationException(Map.of(field,
                    "amount-bearing properties must be bucketed strings, e.g. \"2000-3000\""));
        }
    }

    private static Object normalize(Object value) {
        if (value instanceof String s) {
            return truncate(s, MAX_PROPERTY_VALUE_LENGTH);
        }
        if (value instanceof Boolean || value instanceof Integer || value instanceof Long) {
            return value;
        }
        // Nested objects and arrays are flattened to their string form rather than stored
        // structurally: the analytics schema stays flat and queryable.
        return truncate(String.valueOf(value), MAX_PROPERTY_VALUE_LENGTH);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
