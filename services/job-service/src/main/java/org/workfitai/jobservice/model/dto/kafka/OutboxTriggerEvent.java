package org.workfitai.jobservice.model.dto.kafka;

import org.springframework.context.ApplicationEvent;

public class OutboxTriggerEvent extends ApplicationEvent {
  public OutboxTriggerEvent(Object source) {
    super(source);
  }
}