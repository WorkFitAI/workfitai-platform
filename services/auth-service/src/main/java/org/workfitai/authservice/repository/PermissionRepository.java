package org.workfitai.authservice.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import org.workfitai.authservice.model.Permission;

import java.util.Optional;

@Repository
public interface PermissionRepository extends MongoRepository<Permission,String> {
    Optional<Permission> findByName(String name);
}