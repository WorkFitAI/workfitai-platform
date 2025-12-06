package org.workfitai.notificationservice.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import org.workfitai.notificationservice.model.EmailLog;

import java.time.Instant;
import java.util.List;

@Repository
public interface EmailLogRepository extends MongoRepository<EmailLog, String> {

    List<EmailLog> findByRecipientEmailOrderByTimestampDesc(String email);

    List<EmailLog> findBySourceServiceOrderByTimestampDesc(String sourceService);

    List<EmailLog> findByDeliveredFalseOrderByTimestampDesc();

    List<EmailLog> findByTimestampBetweenOrderByTimestampDesc(Instant start, Instant end);

    long countByDeliveredTrue();

    long countByDeliveredFalse();
}
