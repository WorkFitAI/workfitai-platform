package org.workfitai.authservice.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.workfitai.authservice.dto.kafka.CompanyCreationEvent;

@ExtendWith(MockitoExtension.class)
class CompanyProducerImplTest {

    @Mock KafkaTemplate<String, Object> kafkaTemplate;
    @InjectMocks CompanyProducerImpl producer;

    private CompanyCreationEvent buildEvent() {
        CompanyCreationEvent.CompanyData company = CompanyCreationEvent.CompanyData.builder()
                .companyId("c-id-1")
                .name("AcmeCorp")
                .logoUrl("https://example.com/logo.png")
                .websiteUrl("https://example.com")
                .description("A test company")
                .address("123 Main St")
                .size("50-200")
                .build();

        CompanyCreationEvent.HRManagerData hrManager = CompanyCreationEvent.HRManagerData.builder()
                .username("hrmanager1")
                .email("hrmanager@example.com")
                .build();

        return CompanyCreationEvent.builder()
                .eventId("evt-1")
                .company(company)
                .hrManager(hrManager)
                .build();
    }

    @Test
    void sendCompanyCreation_success_callsKafkaTemplate() {
        CompanyCreationEvent event = buildEvent();

        producer.sendCompanyCreation(event);

        verify(kafkaTemplate).send(any(), any());
    }

    @Test
    void sendCompanyCreation_kafkaThrows_wrapsInRuntimeException() {
        CompanyCreationEvent event = buildEvent();
        when(kafkaTemplate.send(any(), any())).thenThrow(new RuntimeException("Kafka down"));

        assertThatThrownBy(() -> producer.sendCompanyCreation(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to send company creation event");
    }
}
