package org.workfitai.authservice.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import org.workfitai.authservice.model.User;
import org.workfitai.authservice.enums.UserStatus;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    List<User> findByStatus(UserStatus status);

    List<User> findByRolesContainingAndCompanyNo(String role, String companyNo);

    @Query(value = "{'email': ?0}", fields = "{'companyNo': 1}")
    Optional<User> findByEmailForCompanyNo(String hrManagerEmail);
}