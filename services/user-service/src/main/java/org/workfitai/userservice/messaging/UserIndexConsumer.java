package org.workfitai.userservice.messaging;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.workfitai.userservice.dto.kafka.UserChangeEvent;
import org.workfitai.userservice.dto.kafka.UserDocument;

/**
 * Kafka consumer that listens to user change events and updates Elasticsearch
 * index.
 * Provides eventual consistency between PostgreSQL and Elasticsearch.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserIndexConsumer {

    private final ElasticsearchClient elasticsearchClient;
    private static final String INDEX_NAME = "users-index";

    @KafkaListener(topics = "user-change-events", groupId = "user-index-consumer-group", containerFactory = "kafkaListenerContainerFactory")
    public void handleUserChangeEvent(UserChangeEvent event) {
        try {
            log.info("Received user change event: type={}, userId={}, version={}",
                    event.getEventType(), event.getData().getUserId(), event.getData().getVersion());

            switch (event.getEventType()) {
                case "USER_CREATED":
                case "USER_UPDATED":
                case "USER_UNBLOCKED":
                    indexUser(event.getData());
                    break;

                case "USER_DELETED":
                case "USER_BLOCKED":
                    deleteUserFromIndex(event.getData().getUserId());
                    break;

                default:
                    log.warn("Unknown event type: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("Error processing user change event: userId={}, error={}",
                    event.getData().getUserId(), e.getMessage(), e);
            // In production, implement retry logic or dead letter queue
        }
    }

    private void indexUser(UserChangeEvent.UserEventData data) throws Exception {
        UserDocument document = UserDocument.builder()
                .userId(data.getUserId())
                .username(data.getUsername())
                .email(data.getEmail())
                .fullName(data.getFullName())
                .phoneNumber(data.getPhoneNumber())
                .role(data.getUserRole().name())
                .status(data.getUserStatus().name())
                .blocked(data.isBlocked())
                .deleted(data.isDeleted())
                .companyNo(data.getCompanyNo())
                .companyName(data.getCompanyName())
                .createdDate(data.getCreatedDate())
                .lastModifiedDate(data.getLastModifiedDate())
                .version(data.getVersion())
                .build();

        elasticsearchClient.index(i -> i
                .index(INDEX_NAME)
                .id(data.getUserId().toString())
                .document(document)
                .refresh(Refresh.False) // Don't force refresh for better performance
        );

        log.info("Indexed user in Elasticsearch: userId={}, username={}",
                data.getUserId(), data.getUsername());
    }

    private void deleteUserFromIndex(String userId) throws Exception {
        elasticsearchClient.delete(d -> d
                .index(INDEX_NAME)
                .id(userId)
                .refresh(Refresh.False));

        log.info("Deleted user from Elasticsearch index: userId={}", userId);
    }
}
