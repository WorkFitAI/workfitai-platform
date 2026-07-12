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

    /**
     * Fetches all active, non-snapshot CVs (applicationId is null) for a list of usernames.
     * Used by getCvDataBatch so that application snapshots created during a broken-parser
     * period do not shadow the user's correct regular CV.
     */
    List<CV> findByBelongToInAndIsExistTrueAndApplicationIdIsNull(List<String> belongTos);

    /** Finds the immutable snapshot CV created for a specific application. */
    Optional<CV> findByApplicationId(String applicationId);

    /** Batch lookup of snapshot CVs by applicationId — used for active-pool enrichment. */
    List<CV> findByApplicationIdIn(List<String> applicationIds);

    /**
     * Fetches active, PDF-backed CVs that have never had an Ollama section-extraction
     * attempt (ollamaExtractedAt is null) — used by the startup backfill runner to
     * catch up CVs created before the Ollama feature existed, or whose enrichment
     * attempt was dropped on a previous run (executor saturation). Bounded via
     * Pageable so a single startup pass never scans/loads the whole collection.
     *
     * objectName IS NOT NULL excludes template CVs (form-built, no PDF, ollamaExtractedAt
     * never gets set for them since there's no rawText to extract) — without this filter
     * they'd permanently re-populate every batch and could starve out real candidates.
     */
    List<CV> findByOllamaExtractedAtIsNullAndIsExistTrueAndObjectNameIsNotNull(Pageable pageable);
}