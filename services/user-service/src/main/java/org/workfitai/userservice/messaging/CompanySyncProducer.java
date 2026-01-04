package org.workfitai.userservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.workfitai.userservice.dto.kafka.CompanySyncEvent;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class CompanySyncProducer {

  private final KafkaTemplate<String, Object> kafkaTemplate;

  @Value("${app.kafka.topics.company-sync:company-sync}")
  private String companySyncTopic;

  public void publish(CompanySyncEvent event) {
    try {
      CompletableFuture<SendResult<String, Object>> future =
          kafkaTemplate.send(companySyncTopic, event.getCompany().getCompanyId(), event);
      future.whenComplete((result, throwable) -> {
        if (throwable != null) {
          log.error("Failed to publish company sync event {}", event.getCompany().getCompanyId(), throwable);
        } else {
          log.info("Published company sync event for company {} to topic {} offset {}",
              event.getCompany().getCompanyId(),
              result.getRecordMetadata().topic(),
              result.getRecordMetadata().offset());
        }
      });
    } catch (Exception ex) {
      log.error("Error publishing company sync event for company {}", event.getCompany().getCompanyId(), ex);
    }
  }
}
