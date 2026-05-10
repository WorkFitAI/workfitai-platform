package org.workfitai.jobservice.service.impl;

import org.springframework.stereotype.Service;
import org.workfitai.jobservice.model.OutboxExpiredJobEvent;
import org.workfitai.jobservice.repository.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class OutboxService {

  private final OutboxRepository outboxRepository;
  private final ObjectMapper objectMapper;

  public OutboxService(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
    this.outboxRepository = outboxRepository;
    this.objectMapper = objectMapper;
  }

  public void saveEvent(String type, Object payload, String aggregateId) {
    try {
      OutboxExpiredJobEvent event = new OutboxExpiredJobEvent();
      event.setType(type);
      event.setAggregateId(aggregateId);
      event.setPayload(objectMapper.writeValueAsString(payload));
      event.setStatus("NEW");

      outboxRepository.save(event);
    } catch (Exception e) {
      throw new RuntimeException("Failed to serialize event");
    }
  }
}