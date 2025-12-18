package org.workfitai.authservice.messaging;

import org.workfitai.authservice.dto.kafka.CompanyCreationEvent;

public interface CompanyProducer {
    void sendCompanyCreation(CompanyCreationEvent event);
}