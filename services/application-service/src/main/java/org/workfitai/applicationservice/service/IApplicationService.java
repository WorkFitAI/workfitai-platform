package org.workfitai.applicationservice.service;

import org.springframework.data.domain.Pageable;
import org.workfitai.applicationservice.dto.request.CreateApplicationRequest;
import org.workfitai.applicationservice.dto.response.ApplicationResponse;
import org.workfitai.applicationservice.dto.response.ResultPaginationDTO;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;

/**
 * Service interface for Application business operations.
 */
public interface IApplicationService {

        ApplicationResponse createApplication(CreateApplicationRequest request, String username);

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
}
