package app.salary.api.store;

import java.time.Instant;
import java.util.Map;

/**
 * One analytics event as stored.
 *
 * <p>Note what is <em>not</em> here: the provider sub. Events are attributed to
 * {@code accountId} when one is resolvable and to {@code deviceId} otherwise, so this
 * collection never has to be re-keyed by the identity migration.
 *
 * @param accountId null for anonymous senders, and also for anyone whose last sign-in
 *                  predates the identity schema. Attribution falls back to deviceId.
 */
public record EventRecord(String id,
                          String name,
                          String deviceId,
                          String accountId,
                          String client,
                          Instant occurredAt,
                          Instant receivedAt,
                          Map<String, Object> properties) {}
