package org.workfitai.userservice.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.adapter.RecordFilterStrategy;
import org.springframework.stereotype.Component;

/**
 * Kafka message filters for OAuth events
 * Filters messages by eventType to route to correct consumer methods
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuthEventKafkaFilter {

    private final ObjectMapper objectMapper;

    @Component("oauthAccountLinkedFilter")
    public class OAuthAccountLinkedFilter implements RecordFilterStrategy<String, String> {
        @Override
        public boolean filter(ConsumerRecord<String, String> record) {
            try {
                JsonNode json = objectMapper.readTree(record.value());
                String eventType = json.path("eventType").asText();
                return !"OAUTH_ACCOUNT_LINKED".equals(eventType);
            } catch (Exception e) {
                log.warn("Failed to parse message for filtering: {}", record.value(), e);
                return true; // Filter out malformed messages
            }
        }
    }

    @Component("oauthAccountUnlinkedFilter")
    public class OAuthAccountUnlinkedFilter implements RecordFilterStrategy<String, String> {
        @Override
        public boolean filter(ConsumerRecord<String, String> recordRecord) {
            try {
                JsonNode json = objectMapper.readTree(recordRecord.value());
                String eventType = json.path("eventType").asText();
                return !"OAUTH_ACCOUNT_UNLINKED".equals(eventType);
            } catch (Exception e) {
                log.warn("Failed to parse message for filtering: {}", recordRecord.value(), e);
                return true; // Filter out malformed messages
            }
        }
    }
}
