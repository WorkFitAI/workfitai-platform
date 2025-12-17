package org.workfitai.authservice.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import org.workfitai.authservice.document.TwoFactorAuth;

import java.util.Optional;

@Repository
public interface TwoFactorAuthRepository extends MongoRepository<TwoFactorAuth, String> {

    Optional<TwoFactorAuth> findByUserId(String userId);

    void deleteByUserId(String userId);
}
