package org.workfitai.authservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.workfitai.authservice.dto.kafka.CompanyCreationEvent;
import org.workfitai.authservice.messaging.CompanyProducer;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyProducerImpl implements CompanyProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.company-sync:company-sync}")
    private String companyTopic;

    @Override
    public void sendCompanyCreation(CompanyCreationEvent event) {
        try {
            log.info("Sending company creation event for company: {} (HR Manager: {})",
                    event.getCompany().getName(), event.getHrManager().getUsername());

            // Convert to job-service CompanySyncEvent format
            var companySyncEvent = Map.of(
                    "eventId", event.getEventId(),
                    "eventType", "COMPANY_CREATED",
                    "timestamp", Instant.now(),
                    "company", Map.of(
                            "companyId", event.getCompany().getCompanyId(),
                            "name", event.getCompany().getName(),
                            "logoUrl", event.getCompany().getLogoUrl(),
                            "websiteUrl", event.getCompany().getWebsiteUrl(),
                            "description", event.getCompany().getDescription(),
                            "address", event.getCompany().getAddress(),
                            "size", event.getCompany().getSize()));

            kafkaTemplate.send(companyTopic, companySyncEvent);

            log.info("Successfully sent company creation event for company: {}", event.getCompany().getName());

        } catch (Exception e) {
            log.error("Error sending company creation event for company: {}", event.getCompany().getName(), e);
            throw new RuntimeException("Failed to send company creation event", e);
        }
    }
}