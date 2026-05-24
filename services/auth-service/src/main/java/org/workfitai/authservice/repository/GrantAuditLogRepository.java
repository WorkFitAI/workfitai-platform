package org.workfitai.authservice.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.workfitai.authservice.model.GrantAuditLog;

import java.util.List;

public interface GrantAuditLogRepository extends MongoRepository<GrantAuditLog, String> {
    List<GrantAuditLog> findAllByOrderByTimestampDesc();
    List<GrantAuditLog> findByTargetUsernameOrderByTimestampDesc(String targetUsername);
    List<GrantAuditLog> findByGrantorUsernameOrderByTimestampDesc(String grantorUsername);
}
