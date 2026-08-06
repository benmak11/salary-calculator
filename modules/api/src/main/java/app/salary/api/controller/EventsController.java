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
 * First-party analytics ingest. Anonymous requests get 401.
 *
 * <p><b>Known consequence of requiring auth:</b> everything before sign-in is invisible —
 * {@code session_start}, the onboarding events, and a signed-out user's first
 * {@code calculation_completed}. Signed-out installs that never convert produce no data at
 * all. Any funnel metric defined on this endpoint measures post-sign-in behaviour only, and
 * install-to-sign-in conversion has to come from store analytics instead.
 *
 * <p>The {@code deviceId} on each batch still matters: one account spans several devices,
 * and per-device rates (widget installs, notification opt-in) cannot be read from the
 * account alone.
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
        Optional<String> userId = AuthMiddleware.requireUser(ctx);
        if (userId.isEmpty()) return;

        EventBatchRequest batch = ctx.bodyAsClass(EventBatchRequest.class);
        validator.validate(batch);

        String accountId = resolveAccountId(userId.get());
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
     * Maps the session's provider sub onto an accountId, so events are keyed on the
     * identifier that survives the migration rather than on the sub itself.
     *
     * <p>Null is still a normal outcome even now that auth is required: anyone whose last
     * sign-in predates the identity schema has no mapping until they sign in again.
     * Attribution falls back to the batch's deviceId.
     */
    private String resolveAccountId(String userId) {
        if (accounts == null) {
            return null;
        }
        return accounts.findAccountIdBySub(userId).orElse(null);
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
