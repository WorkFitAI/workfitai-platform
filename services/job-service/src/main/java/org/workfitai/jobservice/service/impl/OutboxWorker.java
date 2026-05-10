package org.workfitai.jobservice.service.impl;

import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.workfitai.jobservice.model.dto.kafka.OutboxTriggerEvent;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class OutboxWorker {

  private final OutboxProcessor outboxProcessor;

  OutboxWorker(OutboxProcessor outboxProcessor) {
    this.outboxProcessor = outboxProcessor;
  }

  @Scheduled(cron = "0 0 0 * * ?", zone = "Asia/Ho_Chi_Minh")
  public void process() {
    outboxProcessor.process();
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handle(OutboxTriggerEvent event) {
    log.info("Received OutboxTriggerEvent, starting to process outbox events");
    process();
  }
}
