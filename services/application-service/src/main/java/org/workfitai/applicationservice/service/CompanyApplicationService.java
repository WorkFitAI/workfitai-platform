package org.workfitai.applicationservice.service;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.workfitai.applicationservice.dto.response.ApplicationResponse;
import org.workfitai.applicationservice.dto.response.ResultPaginationDTO;
import org.workfitai.applicationservice.mapper.ApplicationMapper;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.repository.ApplicationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for company-wide application queries.
 *
 * Provides HR Managers with company-level views of applications:
 * - All applications in company
 * - Filter by status, assigned HR
 * - Pagination support
 *
 * Security:
 * - Manager must belong to same company (enforced at controller level)
 * - Company isolation enforced by companyId filter
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyApplicationService {

        private final ApplicationRepository applicationRepository;
        private final ApplicationMapper applicationMapper;
        private final MongoTemplate mongoTemplate;

        /**
         * Get all applications for a company.
         *
         * @param companyId Company ID
         * @param pageable  Pagination parameters
         * @return Paginated application responses
         */
        public ResultPaginationDTO<ApplicationResponse> getCompanyApplications(
                        String companyId,
                        Pageable pageable) {

                log.info("Fetching applications for company: {}", companyId);

                Page<Application> applicationPage = applicationRepository
                                .findByCompanyIdAndDeletedAtIsNull(companyId, pageable);

                return buildPaginatedResult(applicationPage);
        }

        /**
         * Get company applications filtered by status.
         *
         * @param companyId Company ID
         * @param status    Application status
         * @param pageable  Pagination parameters
         * @return Paginated application responses
         */
        public ResultPaginationDTO<ApplicationResponse> getCompanyApplicationsByStatus(
                        String companyId,
                        ApplicationStatus status,
                        Pageable pageable) {

                log.info("Fetching applications for company: {}, status: {}", companyId, status);

                Page<Application> applicationPage = applicationRepository
                                .findByCompanyIdAndStatusAndDeletedAtIsNull(companyId, status, pageable);

                return buildPaginatedResult(applicationPage);
        }

        /**
         * Get company applications assigned to specific HR.
         *
         * @param companyId  Company ID
         * @param assignedTo HR username
         * @param pageable   Pagination parameters
         * @return Paginated application responses
         */
        public ResultPaginationDTO<ApplicationResponse> getCompanyApplicationsByAssignedHR(
                        String companyId,
                        String assignedTo,
                        Pageable pageable) {

                log.info("Fetching applications for company: {}, assignedTo: {}", companyId, assignedTo);

                Page<Application> applicationPage = applicationRepository
                                .findByCompanyIdAndAssignedToAndDeletedAtIsNull(companyId, assignedTo, pageable);

                return buildPaginatedResult(applicationPage);
        }

        /**
         * Get applications assigned to HR user (across all companies - not used in
         * Phase 3).
         *
         * @param assignedTo HR username
         * @param pageable   Pagination parameters
         * @return Paginated application responses
         */
        public ResultPaginationDTO<ApplicationResponse> getAssignedApplications(
                        String assignedTo,
                        Pageable pageable) {

                log.info("Fetching applications assigned to: {}", assignedTo);

                Page<Application> applicationPage = applicationRepository
                                .findByAssignedToAndIsDraftFalseAndDeletedAtIsNull(assignedTo, pageable);

                return buildPaginatedResult(applicationPage);
        }

        /**
         * Get assigned applications filtered by status.
         *
         * @param assignedTo HR username
         * @param status     Application status
         * @param pageable   Pagination parameters
         * @return Paginated application responses
         */
        public ResultPaginationDTO<ApplicationResponse> getAssignedApplicationsByStatus(
                        String assignedTo,
                        ApplicationStatus status,
                        Pageable pageable) {

                log.info("Fetching applications assigned to: {}, status: {}", assignedTo, status);

                Page<Application> applicationPage = applicationRepository
                                .findByAssignedToAndStatusAndIsDraftFalseAndDeletedAtIsNull(assignedTo, status,
                                                pageable);

                return buildPaginatedResult(applicationPage);
        }

        /**
         * Get assigned applications with dynamic filters (status, date range).
         * Uses MongoDB Criteria API for flexible querying.
         *
         * @param assignedTo HR username
         * @param status     Application status (optional)
         * @param fromDate   Start date for createdAt filter (optional)
         * @param toDate     End date for createdAt filter (optional)
         * @param pageable   Pagination parameters
         * @return Paginated application responses
         */
        public ResultPaginationDTO<ApplicationResponse> getAssignedApplicationsWithFilters(
                        String assignedTo,
                        ApplicationStatus status,
                        Instant fromDate,
                        Instant toDate,
                        Pageable pageable) {

                log.info("Fetching assigned applications with filters: assignedTo={}, status={}, fromDate={}, toDate={}",
                                assignedTo, status, fromDate, toDate);

                Criteria criteria = buildAssignedApplicationsCriteria(assignedTo, status, fromDate, toDate);
                Query query = Query.query(criteria).with(pageable);

                // Get total count for pagination
                long totalCount = mongoTemplate.count(Query.query(criteria), Application.class);

                // Get paginated results
                List<Application> applications = mongoTemplate.find(query, Application.class);

                // Map to response DTOs
                List<ApplicationResponse> responses = applications.stream()
                                .map(applicationMapper::toResponse)
                                .toList();

                Page<ApplicationResponse> page = new PageImpl<>(
                                responses,
                                pageable,
                                totalCount);

                log.info("Found {} assigned applications", totalCount);

                return ResultPaginationDTO.of(
                                page.getContent(),
                                page.getNumber(),
                                page.getSize(),
                                page.getTotalElements(),
                                page.getTotalPages());
        }

        /**
         * Build dynamic criteria for assigned applications query.
         *
         * @param assignedTo HR username
         * @param status     Status filter (optional)
         * @param fromDate   From date filter (optional)
         * @param toDate     To date filter (optional)
         * @return Combined criteria
         */
        private Criteria buildAssignedApplicationsCriteria(
                        String assignedTo,
                        ApplicationStatus status,
                        Instant fromDate,
                        Instant toDate) {

                Criteria criteria = new Criteria();

                // Base filters: assignedTo, non-draft, non-deleted
                criteria.and("assignedTo").is(assignedTo);
                criteria.and("isDraft").is(false);
                criteria.and("deletedAt").isNull();

                // Filter by status (optional)
                if (status != null) {
                        criteria.and("status").is(status);
                }

                // Filter by date range (optional)
                if (fromDate != null) {
                        criteria.and("createdAt").gte(fromDate);
                }
                if (toDate != null) {
                        criteria.and("createdAt").lte(toDate);
                }

                return criteria;
        }

        /**
         * Converts Page<Application> to ResultPaginationDTO.
         */
        private ResultPaginationDTO<ApplicationResponse> buildPaginatedResult(Page<Application> page) {
                return ResultPaginationDTO.<ApplicationResponse>builder()
                                .items(page.getContent().stream()
                                                .map(applicationMapper::toResponse)
                                                .toList())
                                .meta(ResultPaginationDTO.Meta.builder()
                                                .page(page.getNumber())
                                                .size(page.getSize())
                                                .totalElements(page.getTotalElements())
                                                .totalPages(page.getTotalPages())
                                                .first(page.isFirst())
                                                .last(page.isLast())
                                                .hasNext(page.hasNext())
                                                .hasPrevious(page.hasPrevious())
                                                .build())
                                .build();
        }
}
