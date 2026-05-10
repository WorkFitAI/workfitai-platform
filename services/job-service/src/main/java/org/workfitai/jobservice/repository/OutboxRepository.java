package org.workfitai.jobservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.workfitai.jobservice.model.OutboxExpiredJobEvent;
import org.workfitai.jobservice.model.enums.EventStatus;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxExpiredJobEvent, Long> {

  List<OutboxExpiredJobEvent> findTop100ByStatusInOrderByCreatedDateAsc(List<EventStatus> statuses);
}