package org.workfitai.applicationservice.service;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.workfitai.applicationservice.dto.response.ApplicationResponse;
import org.workfitai.applicationservice.dto.response.NoteResponse;
import org.workfitai.applicationservice.dto.response.ResultPaginationDTO;
import org.workfitai.applicationservice.dto.response.StatusChangeResponse;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;

/**
 * Service interface for Application business operations.
 *
 * Note: Application creation is handled by ApplicationSagaOrchestrator
 * which orchestrates the multi-step workflow (validation → upload → save →
 * events).
 */
public interface IApplicationService {

        ApplicationResponse getApplicationById(String id);

        ResultPaginationDTO<ApplicationResponse> getMyApplications(String username, Pageable pageable);

        ResultPaginationDTO<ApplicationResponse> getMyApplicationsByStatus(
                        String username,
                        ApplicationStatus status,
                        Pageable pageable);

        ResultPaginationDTO<ApplicationResponse> getApplicationsByJob(String jobId, Pageable pageable);

        ResultPaginationDTO<ApplicationResponse> getApplicationsByJobAndStatus(
                        String jobId,
                        ApplicationStatus status,
                        Pageable pageable);

        boolean hasUserAppliedToJob(String username, String jobId);

        ApplicationResponse updateStatus(String id, ApplicationStatus newStatus, String updatedBy);

        void withdrawApplication(String id, String username);

        long countByUser(String username);

        long countByJob(String jobId);

        /**
         * Get status change history for an application.
         * Authorization is enforced at the controller layer via @PreAuthorize.
         */
        List<StatusChangeResponse> getStatusHistory(String id);

        /**
         * Get public HR notes for an application (visible to candidate).
         * Authorization is enforced at the controller layer via @PreAuthorize.
         */
        List<NoteResponse> getPublicNotes(String id);
}
