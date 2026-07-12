package org.workfitai.cvservice.service;


import jakarta.validation.Valid;
import org.workfitai.cvservice.errors.CVConflictException;
import org.workfitai.cvservice.errors.InvalidDataException;
import org.workfitai.cvservice.model.dto.request.ReqCvDTO;
import org.workfitai.cvservice.model.dto.response.ResCvDTO;
import org.workfitai.cvservice.model.dto.response.ResultPaginationDTO;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.workfitai.cvservice.model.dto.response.CvDataResponse;
import org.workfitai.cvservice.model.dto.response.CvSnapshotResponse;

import java.util.List;


public interface iCVService {


    <T> ResCvDTO createCv(String type, T dto) throws InvalidDataException;


    ResCvDTO getById(String cvId);


    ResultPaginationDTO<ResCvDTO> getCVByBelongTo(String username, int page, int size);


    ResCvDTO update(String cvId, @Valid ReqCvDTO req) throws CVConflictException, InvalidDataException;


    void delete(String cvId) throws CVConflictException, InvalidDataException;


    ResultPaginationDTO<ResCvDTO> getCVByBelongToWithFilter(
            String username,
            Map<String, Object> filters,
            int page,
            int size
    );


    InputStream downloadCV(String objectName) throws Exception;

    /**
     * Returns structured CV data for the given usernames (latest active CV per user).
     * Used by recommendation-engine at startup to warm up CvReferStore.
     */
    List<CvDataResponse> getCvDataBatch(List<String> usernames);

    /**
     * Creates an immutable snapshot CV from a raw PDF uploaded during application submission.
     *
     * Called by application-service (internal endpoint) to extract structured fields
     * (summary, experience, skills, education) from the candidate's CV PDF at apply time.
     * The resulting CV document is stored in MongoDB with {@code applicationId} set so it
     * can be retrieved later without re-parsing.
     *
     * @param username      the candidate's username
     * @param applicationId the application's temporary UUID (assigned by application-service before save)
     * @param jobId         the job's postId — used to re-push Ollama-corrected sections into
     *                      recommendation-engine's CvReferStore for HR ranking
     * @param jobName       title of the job applied to — used to name the stored PDF object
     * @param file          the uploaded CV PDF file bytes
     * @return {@link CvSnapshotResponse} containing cvId and structured extracted fields
     */
    CvSnapshotResponse createApplicationSnapshot(String username, String applicationId, String jobId, String jobName,
                                                  org.springframework.web.multipart.MultipartFile file);

    /**
     * Batch lookup of snapshot CVs by applicationIds.
     * Used by application-service to enrich active-pool responses.
     */
    List<CvSnapshotResponse> getCvSnapshotsByApplicationIds(List<String> applicationIds);

    /**
     * Re-creates a snapshot CV by downloading the candidate's original PDF from
     * {@code cvFileUrl} (the URL application-service stores on the Application record,
     * independent of whether the original SNAPSHOT_CV call ever succeeded).
     *
     * Used by application-service's reconciliation job to backfill applications whose
     * {@code cvSnapshotId} is still null (the original apply-time call to cv-service
     * failed and was never retried successfully).
     *
     * @param username      the candidate's username
     * @param applicationId the application's UUID
     * @param jobName       title of the job applied to — used to name the stored PDF object
     * @param cvFileUrl     URL of the originally uploaded CV PDF (application-service's storage)
     * @return {@link CvSnapshotResponse} containing cvId and structured extracted fields
     */
    CvSnapshotResponse reparseApplicationSnapshot(String username, String applicationId, String jobName,
                                                   String cvFileUrl);
}

