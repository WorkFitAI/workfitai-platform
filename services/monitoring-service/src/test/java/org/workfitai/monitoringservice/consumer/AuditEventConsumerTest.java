package org.workfitai.monitoringservice.consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workfitai.monitoringservice.dto.kafka.AuditEvent;
import org.workfitai.monitoringservice.service.AuditSearchService;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditEventConsumerTest {

    @Mock
    private AuditSearchService auditSearchService;

    private AuditEventConsumer auditEventConsumer;

    private static final AuditEvent SAMPLE_EVENT = new AuditEvent(
            "evt-1", "auth-service", "alice", "ADMIN", "company-1",
            "USER", "user-9", "USER_BLOCKED", null, null,
            Instant.parse("2024-01-01T00:00:00Z"), true, null, "127.0.0.1");

    private void init() {
        auditEventConsumer = new AuditEventConsumer(auditSearchService);
    }

    @Test
    void consume_indexesValidEvent() {
        init();

        auditEventConsumer.consume(SAMPLE_EVENT);

        verify(auditSearchService).index(SAMPLE_EVENT);
    }

    @Test
    void consume_throwsIllegalArgumentException_onNullEvent() {
        init();

        assertThrows(IllegalArgumentException.class, () -> auditEventConsumer.consume(null));
    }

    @Test
    void consume_propagatesException_whenIndexingFails() {
        init();
        doThrow(new RuntimeException("es write failure")).when(auditSearchService).index(SAMPLE_EVENT);

        assertThrows(RuntimeException.class, () -> auditEventConsumer.consume(SAMPLE_EVENT));
    }
}
