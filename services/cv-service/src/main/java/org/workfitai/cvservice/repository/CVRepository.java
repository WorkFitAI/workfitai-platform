package org.workfitai.cvservice.repository;


import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.workfitai.cvservice.model.CV;


public interface CVRepository extends MongoRepository<CV, String> {

    Page<CV> findByBelongToAndIsExistTrue(String belongTo, Pageable pageable);

    /** Fetches all active CVs for a list of usernames — used for cv-refer initial sync. */
    List<CV> findByBelongToInAndIsExistTrue(List<String> belongTos);

    /** Finds the immutable snapshot CV created for a specific application. */
    Optional<CV> findByApplicationId(String applicationId);

    /** Batch lookup of snapshot CVs by applicationId — used for active-pool enrichment. */
    List<CV> findByApplicationIdIn(List<String> applicationIds);
}