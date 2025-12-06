package org.workfitai.jobservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.workfitai.jobservice.dto.kafka.CompanySyncEvent;
import org.workfitai.jobservice.model.Company;
import org.workfitai.jobservice.repository.CompanyRepository;

@Component
@RequiredArgsConstructor
@Slf4j
public class CompanySyncConsumer {

    private final CompanyRepository companyRepository;

    @KafkaListener(topics = "${app.kafka.topics.company-sync:company-sync}", groupId = "${spring.kafka.consumer.group-id:job-service-group}")
    public void handleCompanySync(@Payload CompanySyncEvent event) {
        if (event == null || event.getCompany() == null) {
            log.warn("Received empty company sync event");
            return;
        }

        var data = event.getCompany();
        log.info("Received company sync event (type: {}) for company: {}", event.getEventType(), data.getName());

        try {
            Company company = companyRepository.findById(data.getCompanyId())
                    .orElse(Company.builder().companyNo(data.getCompanyId()).build());

            company.setName(data.getName());
            company.setLogoUrl(data.getLogoUrl());
            company.setWebsiteUrl(data.getWebsiteUrl());
            company.setDescription(data.getDescription());
            company.setAddress(data.getAddress());
            company.setSize(data.getSize());

            Company savedCompany = companyRepository.save(company);

            if ("COMPANY_CREATED".equals(event.getEventType())) {
                log.info("Successfully created new company: {} with ID: {}", savedCompany.getName(),
                        savedCompany.getCompanyNo());
            } else {
                log.info("Successfully updated company: {} with ID: {}", savedCompany.getName(),
                        savedCompany.getCompanyNo());
            }

        } catch (Exception e) {
            log.error("Error processing company sync event for company: {}", data.getName(), e);
            throw new RuntimeException("Failed to process company sync event", e);
        }
    }
}
