package org.workfitai.notificationservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workfitai.notificationservice.dto.kafka.NotificationEvent;
import org.workfitai.notificationservice.strategy.NotificationStrategy;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationOrchestratorTest {

    @Mock
    private NotificationStrategy highPriorityStrategy;
    @Mock
    private NotificationStrategy lowPriorityStrategy;

    private static final NotificationEvent EVENT = NotificationEvent.builder()
            .eventId("evt-1").eventType("TEST").recipientEmail("alice@test.com").build();

    @Test
    void process_returnsFalse_whenEventIsNull() {
        NotificationOrchestrator orchestrator = new NotificationOrchestrator(List.of(highPriorityStrategy));

        boolean result = orchestrator.process(null);

        assertThat(result).isFalse();
        verify(highPriorityStrategy, never()).canHandle(EVENT);
        verify(highPriorityStrategy, never()).process(EVENT);
    }

    @Test
    void process_usesHighestPriorityMatchingStrategy() {
        when(highPriorityStrategy.getPriority()).thenReturn(10);
        when(lowPriorityStrategy.getPriority()).thenReturn(1000);
        when(highPriorityStrategy.canHandle(EVENT)).thenReturn(true);
        when(highPriorityStrategy.process(EVENT)).thenReturn(true);

        NotificationOrchestrator orchestrator = new NotificationOrchestrator(
                List.of(lowPriorityStrategy, highPriorityStrategy));

        boolean result = orchestrator.process(EVENT);

        assertThat(result).isTrue();
        verify(highPriorityStrategy).process(EVENT);
        verify(lowPriorityStrategy, org.mockito.Mockito.never()).process(EVENT);
    }

    @Test
    void process_fallsThroughToNextStrategy_whenFirstThrows() {
        when(highPriorityStrategy.getPriority()).thenReturn(10);
        when(lowPriorityStrategy.getPriority()).thenReturn(1000);
        when(highPriorityStrategy.canHandle(EVENT)).thenReturn(true);
        when(highPriorityStrategy.process(EVENT)).thenThrow(new RuntimeException("boom"));
        when(lowPriorityStrategy.canHandle(EVENT)).thenReturn(true);
        when(lowPriorityStrategy.process(EVENT)).thenReturn(true);

        NotificationOrchestrator orchestrator = new NotificationOrchestrator(
                List.of(highPriorityStrategy, lowPriorityStrategy));

        boolean result = orchestrator.process(EVENT);

        assertThat(result).isTrue();
        verify(lowPriorityStrategy).process(EVENT);
    }

    @Test
    void process_returnsFalse_whenNoStrategyMatches() {
        when(highPriorityStrategy.getPriority()).thenReturn(10);
        when(highPriorityStrategy.canHandle(EVENT)).thenReturn(false);

        NotificationOrchestrator orchestrator = new NotificationOrchestrator(List.of(highPriorityStrategy));

        boolean result = orchestrator.process(EVENT);

        assertThat(result).isFalse();
    }

    @Test
    void getRegisteredStrategies_returnsNameAndPriorityForEach() {
        when(highPriorityStrategy.getPriority()).thenReturn(10);

        NotificationOrchestrator orchestrator = new NotificationOrchestrator(List.of(highPriorityStrategy));

        assertThat(orchestrator.getRegisteredStrategies()).hasSize(1);
        assertThat(orchestrator.getRegisteredStrategies().get(0)).contains("priority: 10");
    }
}
