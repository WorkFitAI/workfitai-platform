package org.workfitai.userservice.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.workfitai.userservice.dto.kafka.CompanySyncEvent;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanySyncProducerTest {

    @Mock KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    CompanySyncProducer producer;

    @Test
    void publish_sendsWithCompanyIdAsKey() {
        ReflectionTestUtils.setField(producer, "companySyncTopic", "company-sync");
        when(kafkaTemplate.send(any(String.class), any(String.class), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        String companyId = UUID.randomUUID().toString();
        CompanySyncEvent.CompanyData companyData = CompanySyncEvent.CompanyData.builder()
                .companyId(companyId)
                .companyNo("TAX001")
                .name("Test Company")
                .build();

        CompanySyncEvent event = CompanySyncEvent.builder()
                .eventId("evt-1")
                .eventType("COMPANY_UPSERT")
                .company(companyData)
                .build();

        producer.publish(event);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("company-sync"), eq(companyId), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).isSameAs(event);
    }
}
