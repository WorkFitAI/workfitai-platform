package org.workfitai.jobservice.service.impl;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.workfitai.jobservice.model.OutboxExpiredJobEvent;
import org.workfitai.jobservice.model.dto.kafka.OutboxTriggerEvent;
import org.workfitai.jobservice.model.enums.EventStatus;
import org.workfitai.jobservice.repository.OutboxRepository;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class OutboxService {

  private final OutboxRepository outboxRepository;
  private final ObjectMapper objectMapper;
  private final ApplicationEventPublisher eventPublisher;

  public OutboxService(OutboxRepository outboxRepository, ObjectMapper objectMapper,
      ApplicationEventPublisher eventPublisher) {
    this.outboxRepository = outboxRepository;
    this.objectMapper = objectMapper;
    this.eventPublisher = eventPublisher;
  }

  @Transactional
  public void saveEvents(List<OutboxExpiredJobEvent> events) {
    outboxRepository.saveAll(events);

    // Trigger the worker immediately after saving events
    eventPublisher.publishEvent(new OutboxTriggerEvent(this));
  }

  public OutboxExpiredJobEvent buildEvent(String type, Object payload, String aggregateId) {
    try {
      OutboxExpiredJobEvent event = new OutboxExpiredJobEvent();
      event.setType(type);
      event.setAggregateId(aggregateId);
      event.setPayload(objectMapper.writeValueAsString(payload));
      event.setStatus(EventStatus.NEW);
      return event;
    } catch (Exception e) {
      throw new RuntimeException("Failed to serialize event");
    }
  }
}