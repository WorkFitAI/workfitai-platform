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
import org.springframework.transaction.annotation.Transactional;
import org.workfitai.applicationservice.dto.response.ApplicationResponse;
import org.workfitai.applicationservice.dto.response.ResultPaginationDTO;
import org.workfitai.applicationservice.mapper.ApplicationMapper;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for advanced application search with dynamic filters.
 *
 * companyId is mandatory for HR users and enforced at the controller layer.
 * Admin callers pass companyId=null to bypass tenant scoping.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ApplicationSearchService {

    private final MongoTemplate mongoTemplate;
    private final ApplicationMapper applicationMapper;

    /**
     * Search applications with multiple filters.
     *
     * @param jobIds     List of job IDs to filter (optional)
     * @param status     Application status to filter (optional)
     * @param fromDate   Start date for createdAt filter (optional)
     * @param toDate     End date for createdAt filter (optional)
     * @param searchText Text to search in cover letter (optional)
     * @param companyId  Caller's company ID from JWT — null means admin (no tenant scoping)
     * @param pageable   Pagination parameters
     * @return Paginated search results
     */
    public ResultPaginationDTO<ApplicationResponse> search(
            List<String> jobIds,
            ApplicationStatus status,
            Instant fromDate,
            Instant toDate,
            String searchText,
            String companyId,
            Pageable pageable) {

        log.info("Searching applications: jobIds={}, status={}, fromDate={}, toDate={}, searchText={}, companyId={}",
                 jobIds, status, fromDate, toDate, searchText != null ? "***" : null, companyId);

        Criteria criteria = buildSearchCriteria(jobIds, status, fromDate, toDate, searchText, companyId);
        Query query = Query.query(criteria).with(pageable);

        long totalCount = mongoTemplate.count(Query.query(criteria), Application.class);
        List<Application> applications = mongoTemplate.find(query, Application.class);

        List<ApplicationResponse> responses = applications.stream()
                .map(applicationMapper::toResponse)
                .toList();

        Page<ApplicationResponse> page = new PageImpl<>(responses, pageable, totalCount);

        log.info("Search completed: found {} results", totalCount);

        return ResultPaginationDTO.of(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }

    private Criteria buildSearchCriteria(
            List<String> jobIds,
            ApplicationStatus status,
            Instant fromDate,
            Instant toDate,
            String searchText,
            String companyId) {

        Criteria criteria = new Criteria();
        criteria.and("deletedAt").isNull();

        // Mandatory tenant scoping — null companyId means admin bypass (C2 fix)
        if (companyId != null) {
            criteria.and("companyId").is(companyId);
        }

        if (jobIds != null && !jobIds.isEmpty()) {
            criteria.and("jobId").in(jobIds);
        }

        if (status != null) {
            criteria.and("status").is(status);
        }

        if (fromDate != null) {
            criteria.and("createdAt").gte(fromDate);
        }
        if (toDate != null) {
            criteria.and("createdAt").lte(toDate);
        }

        if (searchText != null && !searchText.trim().isEmpty()) {
            String escapedText = escapeRegex(searchText.trim());
            criteria.and("coverLetter").regex(escapedText, "i");
        }

        return criteria;
    }

    private String escapeRegex(String text) {
        return text.replaceAll("([.?*+^$\\[\\]\\\\(){}|])", "\\\\$1");
    }
}
