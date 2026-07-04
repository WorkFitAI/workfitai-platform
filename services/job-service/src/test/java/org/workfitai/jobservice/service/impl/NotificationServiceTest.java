package org.workfitai.jobservice.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workfitai.jobservice.dto.kafka.NotificationEvent;
import org.workfitai.jobservice.messaging.NotificationProducer;
import org.workfitai.jobservice.model.dto.kafka.JobExpiredEventDTO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationProducer notificationProducer;
    @InjectMocks
    private NotificationService notificationService;

    @Test
    void sendExpiredNotificationAsync_buildsAndSendsJobExpiredEvent() {
        JobExpiredEventDTO dto = new JobExpiredEventDTO("job-1", "alice", "hr@test.com", "Java Dev", "FPT");

        notificationService.sendExpiredNotificationAsync(dto);

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationProducer).send(captor.capture());
        NotificationEvent event = captor.getValue();
        assertThat(event.getEventType()).isEqualTo("JOB_EXPIRED");
        assertThat(event.getRecipientEmail()).isEqualTo("hr@test.com");
        assertThat(event.getRecipientUserId()).isEqualTo("alice");
        assertThat(event.getSubject()).contains("Java Dev");
        assertThat(event.getMetadata()).containsEntry("jobTitle", "Java Dev").containsEntry("status", "CLOSED");
    }

    @Test
    void sendExpiredNotificationAsync_neverThrows_whenProducerFails() {
        doThrow(new RuntimeException("kafka down")).when(notificationProducer).send(any());
        JobExpiredEventDTO dto = new JobExpiredEventDTO("job-1", "alice", "hr@test.com", "Java Dev", "FPT");

        notificationService.sendExpiredNotificationAsync(dto);
    }
}
