package org.workfitai.applicationservice.service;

import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

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
 * Supports:
 * - Date range filtering (fromDate, toDate)
 * - Multiple job IDs
 * - Text search in cover letter
 * - Status filtering
 * - Pagination with sorting
 *
 * Uses MongoDB Criteria API for building dynamic queries.
 * Performance optimized with compound indexes on (jobId, status, createdAt).
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
     * @param pageable   Pagination parameters
     * @return Paginated search results
     */
    public ResultPaginationDTO<ApplicationResponse> search(
            List<String> jobIds,
            ApplicationStatus status,
            Instant fromDate,
            Instant toDate,
            String searchText,
            Pageable pageable) {

        log.info("Searching applications: jobIds={}, status={}, fromDate={}, toDate={}, searchText={}",
                 jobIds, status, fromDate, toDate, searchText != null ? "***" : null);

        Criteria criteria = buildSearchCriteria(jobIds, status, fromDate, toDate, searchText);
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
            totalCount
        );

        log.info("Search completed: found {} results", totalCount);

        return ResultPaginationDTO.of(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }

    /**
     * Build dynamic search criteria based on provided filters.
     *
     * @param jobIds     Job IDs filter
     * @param status     Status filter
     * @param fromDate   From date filter
     * @param toDate     To date filter
     * @param searchText Text search filter
     * @return Combined criteria
     */
    private Criteria buildSearchCriteria(
            List<String> jobIds,
            ApplicationStatus status,
            Instant fromDate,
            Instant toDate,
            String searchText) {

        Criteria criteria = new Criteria();

        // Exclude soft-deleted applications
        criteria.and("deletedAt").isNull();

        // Filter by job IDs
        if (jobIds != null && !jobIds.isEmpty()) {
            criteria.and("jobId").in(jobIds);
        }

        // Filter by status
        if (status != null) {
            criteria.and("status").is(status);
        }

        // Filter by date range
        if (fromDate != null) {
            criteria.and("createdAt").gte(fromDate);
        }
        if (toDate != null) {
            criteria.and("createdAt").lte(toDate);
        }

        // Text search in cover letter (case-insensitive regex)
        if (searchText != null && !searchText.trim().isEmpty()) {
            // Escape special regex characters to prevent injection
            String escapedText = escapeRegex(searchText.trim());
            criteria.and("coverLetter").regex(escapedText, "i");
        }

        return criteria;
    }

    /**
     * Escape special regex characters to prevent NoSQL injection.
     *
     * @param text Raw search text
     * @return Escaped text safe for regex
     */
    private String escapeRegex(String text) {
        // Escape MongoDB regex special characters
        return text.replaceAll("([.?*+^$\\[\\]\\\\(){}|])", "\\\\$1");
    }
}
