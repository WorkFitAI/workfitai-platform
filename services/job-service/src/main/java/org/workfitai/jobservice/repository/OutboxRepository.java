package org.workfitai.jobservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.workfitai.jobservice.model.OutboxExpiredJobEvent;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxExpiredJobEvent, Long> {

  List<OutboxExpiredJobEvent> findTop10ByStatusIn(List<String> statuses);
}