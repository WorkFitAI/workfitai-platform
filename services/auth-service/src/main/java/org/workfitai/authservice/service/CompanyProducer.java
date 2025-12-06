package org.workfitai.authservice.service;

import org.workfitai.authservice.dto.kafka.CompanyCreationEvent;

public interface CompanyProducer {
    void sendCompanyCreation(CompanyCreationEvent event);
}