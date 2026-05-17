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
 * Provides HR Managers with company-level views with status, assignedTo, and date filters.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyApplicationService {

    private final ApplicationRepository applicationRepository;
    private final ApplicationMapper applicationMapper;
    private final MongoTemplate mongoTemplate;

    public ResultPaginationDTO<ApplicationResponse> getCompanyApplications(
            String companyId, Pageable pageable) {
        log.info("Fetching applications for company: {}", companyId);
        return buildPaginatedResult(
                applicationRepository.findByCompanyIdAndDeletedAtIsNull(companyId, pageable));
    }

    public ResultPaginationDTO<ApplicationResponse> getCompanyApplicationsByStatus(
            String companyId, ApplicationStatus status, Pageable pageable) {
        log.info("Fetching applications for company: {}, status: {}", companyId, status);
        return buildPaginatedResult(
                applicationRepository.findByCompanyIdAndStatusAndDeletedAtIsNull(companyId, status, pageable));
    }

    public ResultPaginationDTO<ApplicationResponse> getCompanyApplicationsByAssignedHR(
            String companyId, String assignedTo, Pageable pageable) {
        log.info("Fetching applications for company: {}, assignedTo: {}", companyId, assignedTo);
        return buildPaginatedResult(
                applicationRepository.findByCompanyIdAndAssignedToAndDeletedAtIsNull(companyId, assignedTo, pageable));
    }

    public ResultPaginationDTO<ApplicationResponse> getCompanyApplicationsByAssignedHRAndStatus(
            String companyId, String assignedTo, ApplicationStatus status, Pageable pageable) {
        log.info("Fetching applications for company: {}, assignedTo: {}, status: {}", companyId, assignedTo, status);

        Criteria criteria = Criteria.where("companyId").is(companyId)
                .and("assignedTo").is(assignedTo)
                .and("status").is(status)
                .and("deletedAt").isNull();

        long total = mongoTemplate.count(Query.query(criteria), Application.class);
        List<Application> applications = mongoTemplate.find(Query.query(criteria).with(pageable), Application.class);
        List<ApplicationResponse> responses = applications.stream().map(applicationMapper::toResponse).toList();

        return toResultPaginationDTO(new PageImpl<>(responses, pageable, total));
    }

    /** Dynamic filter: any combination of status, assignedTo, and jobTitle (case-insensitive partial match). */
    public ResultPaginationDTO<ApplicationResponse> getCompanyApplicationsWithFilters(
            String companyId,
            ApplicationStatus status,
            String assignedTo,
            String jobTitle,
            Pageable pageable) {

        log.info("Fetching company apps with filters: companyId={}, status={}, assignedTo={}, jobTitle={}",
                companyId, status, assignedTo, jobTitle);

        Criteria criteria = Criteria.where("companyId").is(companyId).and("deletedAt").isNull();

        if (status != null) {
            criteria.and("status").is(status);
        }
        if (assignedTo != null && !assignedTo.isBlank()) {
            criteria.and("assignedTo").is(assignedTo);
        }
        if (jobTitle != null && !jobTitle.isBlank()) {
            criteria.and("jobSnapshot.title").regex(jobTitle.trim(), "i");
        }

        long total = mongoTemplate.count(Query.query(criteria), Application.class);
        List<Application> applications = mongoTemplate.find(Query.query(criteria).with(pageable), Application.class);
        List<ApplicationResponse> responses = applications.stream().map(applicationMapper::toResponse).toList();

        return toResultPaginationDTO(new PageImpl<>(responses, pageable, total));
    }

    public ResultPaginationDTO<ApplicationResponse> getAssignedApplications(
            String assignedTo, Pageable pageable) {
        log.info("Fetching applications assigned to: {}", assignedTo);
        return buildPaginatedResult(
                applicationRepository.findByAssignedToAndDeletedAtIsNull(assignedTo, pageable));
    }

    public ResultPaginationDTO<ApplicationResponse> getAssignedApplicationsByStatus(
            String assignedTo, ApplicationStatus status, Pageable pageable) {
        log.info("Fetching applications assigned to: {}, status: {}", assignedTo, status);
        return buildPaginatedResult(
                applicationRepository.findByAssignedToAndStatusAndDeletedAtIsNull(assignedTo, status, pageable));
    }

    /** Dynamic filter: assignedTo + optional status + optional date range. */
    public ResultPaginationDTO<ApplicationResponse> getAssignedApplicationsWithFilters(
            String assignedTo,
            ApplicationStatus status,
            Instant fromDate,
            Instant toDate,
            Pageable pageable) {

        log.info("Fetching assigned apps with filters: assignedTo={}, status={}, fromDate={}, toDate={}",
                assignedTo, status, fromDate, toDate);

        Criteria criteria = Criteria.where("assignedTo").is(assignedTo).and("deletedAt").isNull();

        if (status != null) {
            criteria.and("status").is(status);
        }
        if (fromDate != null) {
            criteria.and("createdAt").gte(fromDate);
        }
        if (toDate != null) {
            criteria.and("createdAt").lte(toDate);
        }

        long total = mongoTemplate.count(Query.query(criteria), Application.class);
        List<Application> applications = mongoTemplate.find(Query.query(criteria).with(pageable), Application.class);
        List<ApplicationResponse> responses = applications.stream().map(applicationMapper::toResponse).toList();

        return toResultPaginationDTO(new PageImpl<>(responses, pageable, total));
    }

    private ResultPaginationDTO<ApplicationResponse> buildPaginatedResult(Page<Application> page) {
        Page<ApplicationResponse> responsePage = page.map(applicationMapper::toResponse);
        return toResultPaginationDTO(responsePage);
    }

    private <T> ResultPaginationDTO<T> toResultPaginationDTO(Page<T> page) {
        return ResultPaginationDTO.<T>builder()
                .items(page.getContent())
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
