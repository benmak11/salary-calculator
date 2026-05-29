package app.salary.rulepack.event;

import app.salary.rulepack.entity.RulePackEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * Publishes rule pack lifecycle events to Google Cloud Pub/Sub.
 *
 * Replaces Spring Cloud GCP's {@code PubSubTemplate} with the native
 * {@link Publisher} client. The publisher is null when the service is running
 * without GCP credentials (e.g. local Docker without ADC) — in that case events
 * are silently dropped after a warning log.
 */
public class RulePackLifecyclePublisher {

    private static final Logger logger = LoggerFactory.getLogger(RulePackLifecyclePublisher.class);

    private final Publisher publisher;          // may be null in local mode
    private final ObjectMapper objectMapper;
    private final String topic;

    public RulePackLifecyclePublisher(Publisher publisher, ObjectMapper objectMapper, String topic) {
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    public void publish(String eventType, RulePackEntity entity) {
        if (publisher == null) {
            logger.debug("Pub/Sub publisher not configured — skipping {} event for {}",
                    eventType, entity.getId());
            return;
        }
        try {
            RulePackLifecycleEvent event = new RulePackLifecycleEvent(
                    eventType,
                    entity.getCountryCode(),
                    entity.getTaxYear(),
                    entity.getVersion(),
                    entity.getStoragePath()
            );
            String payload = objectMapper.writeValueAsString(event);
            PubsubMessage message = PubsubMessage.newBuilder()
                    .setData(ByteString.copyFrom(payload, StandardCharsets.UTF_8))
                    .putAttributes("eventType", eventType)
                    .build();
            publisher.publish(message);
            logger.info("Published {} event for rule pack {} to topic {}",
                    eventType, entity.getId(), topic);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize lifecycle event for rule pack {}", entity.getId(), e);
        } catch (Exception e) {
            logger.error("Failed to publish lifecycle event for rule pack {}", entity.getId(), e);
        }
    }

    public void shutdown() {
        if (publisher != null) {
            try {
                publisher.shutdown();
            } catch (Exception e) {
                logger.warn("Error shutting down Pub/Sub publisher", e);
            }
        }
    }
}
