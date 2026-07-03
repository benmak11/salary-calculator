package app.salary.rulepack.event;

import app.salary.rulepack.entity.RulePackEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.pubsub.v1.PubsubMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RulePackLifecyclePublisherTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RulePackEntity entity() {
        return RulePackEntity.builder()
                .id("id-1")
                .countryCode("US")
                .taxYear(2025)
                .version("US-2025.1.0")
                .storagePath("rulepack/US/2025/US-2025.1.0.json")
                .build();
    }

    @Test
    void publish_sendsMessageWithEventPayloadAndAttribute() throws Exception {
        Publisher pubsub = mock(Publisher.class);
        RulePackLifecyclePublisher publisher =
                new RulePackLifecyclePublisher(pubsub, MAPPER, "rule-pack-lifecycle");

        publisher.publish("RULE_PACK_PUBLISHED", entity());

        ArgumentCaptor<PubsubMessage> message = ArgumentCaptor.forClass(PubsubMessage.class);
        verify(pubsub).publish(message.capture());
        assertEquals("RULE_PACK_PUBLISHED", message.getValue().getAttributesOrThrow("eventType"));

        RulePackLifecycleEvent event = MAPPER.readValue(
                message.getValue().getData().toStringUtf8(), RulePackLifecycleEvent.class);
        assertEquals("RULE_PACK_PUBLISHED", event.getEvent());
        assertEquals("US", event.getCountry());
        assertEquals(2025, event.getTaxYear());
        assertEquals("US-2025.1.0", event.getVersion());
        assertEquals("rulepack/US/2025/US-2025.1.0.json", event.getStoragePath());
    }

    @Test
    void publish_withNoPublisherConfigured_isSilentlySkipped() {
        RulePackLifecyclePublisher publisher =
                new RulePackLifecyclePublisher(null, MAPPER, "rule-pack-lifecycle");

        assertDoesNotThrow(() -> publisher.publish("RULE_PACK_PUBLISHED", entity()));
    }

    @Test
    void publish_whenSerializationFails_isSwallowed() throws Exception {
        Publisher pubsub = mock(Publisher.class);
        ObjectMapper failing = mock(ObjectMapper.class);
        when(failing.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {});
        RulePackLifecyclePublisher publisher =
                new RulePackLifecyclePublisher(pubsub, failing, "rule-pack-lifecycle");

        assertDoesNotThrow(() -> publisher.publish("RULE_PACK_PUBLISHED", entity()));
        verify(pubsub, never()).publish(any());
    }

    @Test
    void publish_whenPubSubFails_isSwallowed() {
        Publisher pubsub = mock(Publisher.class);
        when(pubsub.publish(any())).thenThrow(new RuntimeException("pubsub down"));
        RulePackLifecyclePublisher publisher =
                new RulePackLifecyclePublisher(pubsub, MAPPER, "rule-pack-lifecycle");

        assertDoesNotThrow(() -> publisher.publish("RULE_PACK_PUBLISHED", entity()));
    }

    @Test
    void shutdown_stopsUnderlyingPublisher() {
        Publisher pubsub = mock(Publisher.class);
        RulePackLifecyclePublisher publisher =
                new RulePackLifecyclePublisher(pubsub, MAPPER, "rule-pack-lifecycle");

        publisher.shutdown();

        verify(pubsub).shutdown();
    }

    @Test
    void shutdown_withNoPublisher_isNoOp() {
        RulePackLifecyclePublisher publisher =
                new RulePackLifecyclePublisher(null, MAPPER, "rule-pack-lifecycle");

        assertDoesNotThrow(publisher::shutdown);
    }

    @Test
    void shutdown_whenUnderlyingShutdownFails_isSwallowed() {
        Publisher pubsub = mock(Publisher.class);
        doThrow(new RuntimeException("already stopped")).when(pubsub).shutdown();
        RulePackLifecyclePublisher publisher =
                new RulePackLifecyclePublisher(pubsub, MAPPER, "rule-pack-lifecycle");

        assertDoesNotThrow(publisher::shutdown);
    }
}
