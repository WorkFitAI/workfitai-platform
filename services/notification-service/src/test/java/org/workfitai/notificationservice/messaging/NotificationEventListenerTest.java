package org.workfitai.notificationservice.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workfitai.notificationservice.dto.kafka.NotificationEvent;
import org.workfitai.notificationservice.service.NotificationOrchestrator;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private NotificationOrchestrator orchestrator;

    private NotificationEventListener listener;

    private void init() {
        listener = new NotificationEventListener(orchestrator);
    }

    @Test
    void handleNotification_doesNothing_whenEventNull() {
        init();

        listener.handleNotification(null);

        verify(orchestrator, never()).process(any());
    }

    @Test
    void handleNotification_delegatesToOrchestrator_onSuccess() {
        init();
        NotificationEvent event = NotificationEvent.builder().eventId("evt-1").build();
        when(orchestrator.process(event)).thenReturn(true);

        listener.handleNotification(event);

        verify(orchestrator).process(event);
    }

    @Test
    void handleNotification_logsWarning_whenOrchestratorReturnsFalse() {
        init();
        NotificationEvent event = NotificationEvent.builder().eventId("evt-1").build();
        when(orchestrator.process(event)).thenReturn(false);

        listener.handleNotification(event);

        verify(orchestrator).process(event);
    }

    @Test
    void handleNotification_swallowsException_whenOrchestratorThrows() {
        init();
        NotificationEvent event = NotificationEvent.builder().eventId("evt-1").build();
        when(orchestrator.process(event)).thenThrow(new RuntimeException("boom"));

        listener.handleNotification(event);
        // no exception propagated
    }
}
