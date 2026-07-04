package org.workfitai.jobservice.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workfitai.jobservice.model.dto.kafka.OutboxTriggerEvent;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxWorkerTest {

    @Mock
    private OutboxProcessor outboxProcessor;
    @InjectMocks
    private OutboxWorker outboxWorker;

    @Test
    void process_delegatesToProcessor() {
        outboxWorker.process();

        verify(outboxProcessor).process();
    }

    @Test
    void handle_triggersProcessOnOutboxTriggerEvent() {
        outboxWorker.handle(new OutboxTriggerEvent(this));

        verify(outboxProcessor, times(1)).process();
    }
}
