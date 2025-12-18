package org.workfitai.userservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.workfitai.userservice.model.UserEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID>, JpaSpecificationExecutor<UserEntity> {

  Optional<UserEntity> findByEmail(String email);

  Optional<UserEntity> findByPhoneNumber(String phoneNumber);

  Optional<UserEntity> findByUsername(String username);

  List<UserEntity> findAllByUsernameIn(List<String> usernames);

  boolean existsByEmail(String email);

  boolean existsByPhoneNumber(String phoneNumber);

  boolean existsByUsername(String username);

  /**
   * Find all users with HrProfile eagerly fetched for reindexing
   */
  @Query("SELECT u FROM UserEntity u LEFT JOIN FETCH u.hrProfile")
  Page<UserEntity> findAllWithHrProfile(Pageable pageable);

}
