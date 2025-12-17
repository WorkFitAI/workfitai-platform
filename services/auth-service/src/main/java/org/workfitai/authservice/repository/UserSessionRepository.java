package org.workfitai.authservice.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import org.workfitai.authservice.model.UserSession;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends MongoRepository<UserSession, String> {

    List<UserSession> findByUserIdOrderByCreatedAtDesc(String userId);

    Optional<UserSession> findBySessionId(String sessionId);

    Optional<UserSession> findByUserIdAndSessionId(String userId, String sessionId);

    long countByUserId(String userId);

    void deleteByUserId(String userId);

    void deleteByUserIdAndSessionId(String userId, String sessionId);

    List<UserSession> findByUserIdAndSessionIdNot(String userId, String currentSessionId);
}
