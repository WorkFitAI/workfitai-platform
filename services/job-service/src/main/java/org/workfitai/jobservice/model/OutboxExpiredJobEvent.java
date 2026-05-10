package org.workfitai.jobservice.model;

import jakarta.persistence.Entity;
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
public class OutboxExpiredJobEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String aggregateId;
  private String type;

  @Lob
  private String payload;

  private int retryCount = 0;

  private String status; // NEW, SENT
}
