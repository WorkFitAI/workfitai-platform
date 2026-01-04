package org.workfitai.cvservice.repository;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.workfitai.cvservice.model.CV;


public interface CVRepository extends MongoRepository<CV, String> {

    Page<CV> findByBelongToAndIsExistTrue(String belongTo, Pageable pageable);
}