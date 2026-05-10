package org.workfitai.jobservice.model;

import org.workfitai.jobservice.model.enums.EventStatus;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "outbox_expired_job_event")
@Getter
@Setter
public class OutboxExpiredJobEvent extends AbstractAuditingEntity<Long> {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String aggregateId;
  private String type;

  @Lob
  private String payload;

  private int retryCount = 0;

  @Enumerated(EnumType.STRING)
  private EventStatus status;
}
