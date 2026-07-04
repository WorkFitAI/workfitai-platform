package org.workfitai.userservice.messaging;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workfitai.userservice.dto.kafka.UserChangeEvent;
import org.workfitai.userservice.enums.EUserRole;
import org.workfitai.userservice.enums.EUserStatus;

import java.util.UUID;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserIndexConsumerTest {

    @Mock ElasticsearchClient elasticsearchClient;

    @InjectMocks
    UserIndexConsumer consumer;

    private UserChangeEvent.UserEventData eventData;

    @BeforeEach
    void setUp() {
        eventData = UserChangeEvent.UserEventData.builder()
                .userId(UUID.randomUUID().toString())
                .username("user1")
                .email("u@test.com")
                .fullName("Test User")
                .userRole(EUserRole.CANDIDATE)
                .userStatus(EUserStatus.ACTIVE)
                .isBlocked(false)
                .isDeleted(false)
                .version(1L)
                .build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleUserChangeEvent_userCreated_indexesUser() throws Exception {
        IndexResponse indexResponse = mock(IndexResponse.class);
        when(elasticsearchClient.index(any(Function.class))).thenReturn(indexResponse);

        consumer.handleUserChangeEvent(buildEvent("USER_CREATED"));

        verify(elasticsearchClient).index(any(Function.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleUserChangeEvent_userUpdated_indexesUser() throws Exception {
        IndexResponse indexResponse = mock(IndexResponse.class);
        when(elasticsearchClient.index(any(Function.class))).thenReturn(indexResponse);

        consumer.handleUserChangeEvent(buildEvent("USER_UPDATED"));

        verify(elasticsearchClient).index(any(Function.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleUserChangeEvent_userUnblocked_indexesUser() throws Exception {
        IndexResponse indexResponse = mock(IndexResponse.class);
        when(elasticsearchClient.index(any(Function.class))).thenReturn(indexResponse);

        consumer.handleUserChangeEvent(buildEvent("USER_UNBLOCKED"));

        verify(elasticsearchClient).index(any(Function.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleUserChangeEvent_userDeleted_deletesFromIndex() throws Exception {
        DeleteResponse deleteResponse = mock(DeleteResponse.class);
        when(elasticsearchClient.delete(any(Function.class))).thenReturn(deleteResponse);

        consumer.handleUserChangeEvent(buildEvent("USER_DELETED"));

        verify(elasticsearchClient).delete(any(Function.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleUserChangeEvent_userBlocked_deletesFromIndex() throws Exception {
        DeleteResponse deleteResponse = mock(DeleteResponse.class);
        when(elasticsearchClient.delete(any(Function.class))).thenReturn(deleteResponse);

        consumer.handleUserChangeEvent(buildEvent("USER_BLOCKED"));

        verify(elasticsearchClient).delete(any(Function.class));
    }

    @Test
    void handleUserChangeEvent_unknownEventType_logsWarnAndNoEsCall() throws Exception {
        consumer.handleUserChangeEvent(buildEvent("UNKNOWN_EVENT"));

        verifyNoInteractions(elasticsearchClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleUserChangeEvent_esThrows_swallowsException() throws Exception {
        when(elasticsearchClient.index(any(Function.class)))
                .thenThrow(new RuntimeException("ES unavailable"));

        // Should not propagate
        consumer.handleUserChangeEvent(buildEvent("USER_CREATED"));
    }

    private UserChangeEvent buildEvent(String eventType) {
        return UserChangeEvent.builder()
                .eventId("evt-1")
                .eventType(eventType)
                .userId(eventData.getUserId())
                .version(1L)
                .data(eventData)
                .build();
    }
}
