package org.workfitai.applicationservice.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.workfitai.applicationservice.dto.response.HRAuditActivityResponse;
import org.workfitai.applicationservice.dto.response.HRUserResponse;
import org.workfitai.applicationservice.model.AuditLog;
import org.workfitai.applicationservice.repository.AuditLogRepository;
import org.workfitai.applicationservice.repository.ApplicationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing company HR operations.
 * 
 * Provides functionality for:
 * - Retrieving distinct HR users from audit logs in a specific company
 * - Tracking audit activities of HR users
 * - Company-specific HR management features
 * 
 * Data Source:
 * - Uses local MongoDB collections (audit_logs, applications)
 * - Does not call user-service (temporary solution)
 * - Extracts HR usernames from audit logs
 * 
 * Security:
 * - Only accessible by HR_MANAGER or ADMIN roles
 * - Company isolation enforced at controller level
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyHRService {

    private final AuditLogRepository auditLogRepository;
    private final ApplicationRepository applicationRepository;
    private final MongoTemplate mongoTemplate;

    /**
     * Get all distinct HR users (HR and HR_MANAGER) who have performed actions in a
     * specific company.
     * 
     * This method uses MongoDB aggregation on audit_logs to find distinct HR
     * usernames
     * who have performed actions on applications belonging to the specified
     * company.
     * 
     * Note: This is a temporary solution that only shows HR users who have audit
     * activity.
     * In the future, this should call user-service for complete HR user listing.
     * 
     * @param companyId Company ID to filter HR users
     * @return List of HR usernames with basic info extracted from audit logs
     */
    @SuppressWarnings("unchecked")
    public List<HRUserResponse> getCompanyHRUsers(String companyId) {
        log.info("Fetching distinct HR users from audit logs for company: {}", companyId);

        // Step 1: Get all audit logs for applications in this company
        // Use MongoDB aggregation to find distinct performers
        Aggregation aggregation = Aggregation.newAggregation(
                // Match audit logs for applications (entityType = "APPLICATION")
                Aggregation.match(Criteria.where("entityType").is("APPLICATION")),

                // Group by performedBy to get distinct usernames
                Aggregation.group("performedBy")
                        .first("performedBy").as("username")
                        .count().as("actionCount"),

                // Sort by action count descending
                Aggregation.sort(org.springframework.data.domain.Sort.Direction.DESC, "actionCount"));

        AggregationResults<Map<String, Object>> results = mongoTemplate.aggregate(
                aggregation, "audit_logs", (Class<Map<String, Object>>) (Class<?>) Map.class);

        // Step 2: Filter to only include users from this company's applications
        // Get distinct assignedTo and assignedBy from applications in this company
        List<String> companyHRUsernames = applicationRepository.findByCompanyId(companyId).stream()
                .flatMap(app -> {
                    List<String> usernames = new java.util.ArrayList<>();
                    if (app.getAssignedTo() != null)
                        usernames.add(app.getAssignedTo());
                    if (app.getAssignedBy() != null)
                        usernames.add(app.getAssignedBy());
                    return usernames.stream();
                })
                .distinct()
                .collect(Collectors.toList());

        // Step 3: Build response with usernames from audit logs
        List<HRUserResponse> hrUsers = results.getMappedResults().stream()
                .map(result -> (String) result.get("username"))
                .filter(companyHRUsernames::contains) // Only include company's HR users
                .map(username -> HRUserResponse.builder()
                        .userId(username) // Temporary: use username as userId
                        .username(username)
                        .fullName("HR User") // Placeholder - no full name available
                        .email(username) // Placeholder - no email available
                        .userRole("HR") // Placeholder - role not available from audit logs
                        .companyId(companyId)
                        .build())
                .collect(Collectors.toList());

        log.info("Found {} distinct HR users from audit logs for company: {}", hrUsers.size(), companyId);
        return hrUsers;
    }

    /**
     * Get audit activities for all HR users in a specific company.
     * 
     * This method:
     * 1. Gets all HR usernames from company applications (assignedTo, assignedBy)
     * 2. Retrieves audit logs for those HR users with optional date filtering
     * 3. Returns paginated results with basic user info
     * 
     * Note: Performer details are limited since we don't call user-service.
     * 
     * @param companyId Company ID to filter audit activities
     * @param fromDate  Optional start date for filtering
     * @param toDate    Optional end date for filtering
     * @param pageable  Pagination parameters
     * @return Paginated audit activities of HR users
     */
    public Page<HRAuditActivityResponse> getCompanyHRAuditActivities(
            String companyId,
            Instant fromDate,
            Instant toDate,
            Pageable pageable) {

        log.info("Fetching HR audit activities for company: {}, fromDate: {}, toDate: {}",
                companyId, fromDate, toDate);

        // Step 1: Get all HR usernames from applications in this company
        List<String> hrUsernames = applicationRepository.findByCompanyId(companyId).stream()
                .flatMap(app -> {
                    List<String> usernames = new java.util.ArrayList<>();
                    if (app.getAssignedTo() != null)
                        usernames.add(app.getAssignedTo());
                    if (app.getAssignedBy() != null)
                        usernames.add(app.getAssignedBy());
                    return usernames.stream();
                })
                .distinct()
                .collect(Collectors.toList());

        if (hrUsernames.isEmpty()) {
            log.info("No HR users found for company: {}", companyId);
            return Page.empty(pageable);
        }

        log.debug("Querying audit logs for {} HR users", hrUsernames.size());

        // Step 2: Query audit logs for these HR users
        Page<AuditLog> auditLogs = queryAuditLogsForHRUsers(
                hrUsernames, fromDate, toDate, pageable);

        // Step 3: Transform audit logs to response DTOs
        List<HRAuditActivityResponse> activities = auditLogs.getContent().stream()
                .map(auditLog -> HRAuditActivityResponse.builder()
                        .id(auditLog.getId())
                        .entityType(auditLog.getEntityType())
                        .entityId(auditLog.getEntityId())
                        .action(auditLog.getAction())
                        .performedBy(auditLog.getPerformedBy())
                        .performedAt(auditLog.getPerformedAt())
                        .metadata(auditLog.getMetadata())
                        .performerFullName(auditLog.getPerformedBy()) // Temporary: use username
                        .performerRole("HR") // Placeholder: role not available
                        .build())
                .collect(Collectors.toList());

        log.info("Found {} audit activities for company HR users", activities.size());

        return new PageImpl<>(activities, pageable, auditLogs.getTotalElements());
    }

    /**
     * Query audit logs for a list of HR usernames with optional date filtering.
     * 
     * Uses MongoDB's $in operator for efficient querying.
     * 
     * @param hrUsernames List of HR usernames to query
     * @param fromDate    Optional start date
     * @param toDate      Optional end date
     * @param pageable    Pagination parameters
     * @return Page of audit logs
     */
    private Page<AuditLog> queryAuditLogsForHRUsers(
            List<String> hrUsernames,
            Instant fromDate,
            Instant toDate,
            Pageable pageable) {

        // If date range is provided, use date-filtered query
        if (fromDate != null && toDate != null) {
            return auditLogRepository.findByPerformedByInAndPerformedAtBetweenOrderByPerformedAtDesc(
                    hrUsernames, fromDate, toDate, pageable);
        }

        // Otherwise, query all audit logs for HR users
        return auditLogRepository.findByPerformedByInOrderByPerformedAtDesc(
                hrUsernames, pageable);
    }
}
