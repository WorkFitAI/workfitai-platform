package org.workfitai.jobservice.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.workfitai.jobservice.model.OutboxExpiredJobEvent;
import org.workfitai.jobservice.model.dto.kafka.JobExpiredEventDTO;
import org.workfitai.jobservice.model.dto.kafka.OutboxTriggerEvent;
import org.workfitai.jobservice.model.enums.EventStatus;
import org.workfitai.jobservice.repository.OutboxRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock
    private OutboxRepository outboxRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks
    private OutboxService outboxService;

    @Test
    void saveEvents_persistsAllAndPublishesTrigger() {
        List<OutboxExpiredJobEvent> events = List.of(new OutboxExpiredJobEvent());

        outboxService.saveEvents(events);

        verify(outboxRepository).saveAll(events);
        ArgumentCaptor<OutboxTriggerEvent> captor = ArgumentCaptor.forClass(OutboxTriggerEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isNotNull();
    }

    @Test
    void buildEvent_serializesPayloadAndSetsNewStatus() {
        JobExpiredEventDTO dto = new JobExpiredEventDTO("job-1", "alice", "hr@test.com", "Java Dev", "FPT");

        OutboxExpiredJobEvent event = outboxService.buildEvent("JOB_EXPIRED", dto, "job-1");

        assertThat(event.getType()).isEqualTo("JOB_EXPIRED");
        assertThat(event.getAggregateId()).isEqualTo("job-1");
        assertThat(event.getStatus()).isEqualTo(EventStatus.NEW);
        assertThat(event.getPayload()).contains("job-1").contains("Java Dev");
    }

    @Test
    void buildEvent_throwsRuntimeException_whenSerializationFails() {
        // Plain Object has zero bean properties; Jackson's default FAIL_ON_EMPTY_BEANS
        // rejects it, giving a genuine serialization failure to exercise the catch branch.
        Object unserializable = new Object();

        assertThatThrownBy(() -> outboxService.buildEvent("JOB_EXPIRED", unserializable, "job-1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to serialize event");
    }
}
