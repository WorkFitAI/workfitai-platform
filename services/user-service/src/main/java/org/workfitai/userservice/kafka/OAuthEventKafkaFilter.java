package org.workfitai.userservice.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.adapter.RecordFilterStrategy;
import org.springframework.stereotype.Component;
import org.workfitai.userservice.dto.kafka.OAuthAccountLinkedEvent;
import org.workfitai.userservice.dto.kafka.OAuthAccountUnlinkedEvent;

/**
 * Kafka message filters for OAuth events
 * Filters messages by eventType to route to correct consumer methods
 * Note: Messages are already deserialized to specific event objects by Kafka
 */
@Slf4j
public class OAuthEventKafkaFilter {

    @Component("oauthAccountLinkedFilter")
    public static class OAuthAccountLinkedFilter implements RecordFilterStrategy<String, Object> {
        @Override
        public boolean filter(ConsumerRecord<String, Object> record) {
            try {
                Object value = record.value();
                if (value == null) {
                    log.warn("Received null event, filtering out");
                    return true;
                }

                // Only accept OAuthAccountLinkedEvent
                boolean shouldAccept = value instanceof OAuthAccountLinkedEvent;
                if (shouldAccept) {
                    log.debug("Accepting OAuthAccountLinkedEvent: {}",
                            ((OAuthAccountLinkedEvent) value).getEventId());
                }
                return !shouldAccept;
            } catch (Exception e) {
                log.warn("Failed to filter message: {}", record.value(), e);
                return true; // Filter out malformed messages
            }
        }
    }

    @Component("oauthAccountUnlinkedFilter")
    public static class OAuthAccountUnlinkedFilter implements RecordFilterStrategy<String, Object> {
        @Override
        public boolean filter(ConsumerRecord<String, Object> record) {
            try {
                Object value = record.value();
                if (value == null) {
                    log.warn("Received null event, filtering out");
                    return true;
                }

                // Only accept OAuthAccountUnlinkedEvent
                boolean shouldAccept = value instanceof OAuthAccountUnlinkedEvent;
                if (shouldAccept) {
                    log.debug("Accepting OAuthAccountUnlinkedEvent: {}",
                            ((OAuthAccountUnlinkedEvent) value).getEventId());
                }
                return !shouldAccept;
            } catch (Exception e) {
                log.warn("Failed to filter message: {}", record.value(), e);
                return true; // Filter out malformed messages
            }
        }
    }
}
