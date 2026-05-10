package org.workfitai.applicationservice.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.workfitai.applicationservice.client.UserServiceClient;
import org.workfitai.applicationservice.dto.response.HRAuditActivityResponse;
import org.workfitai.applicationservice.dto.response.HRUserResponse;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.model.AuditLog;
import org.workfitai.applicationservice.repository.AuditLogRepository;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for company HR operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyHRService {

    private final AuditLogRepository auditLogRepository;
    private final MongoTemplate mongoTemplate;
    private final UserServiceClient userServiceClient;

    public List<HRUserResponse> getCompanyHRUsers(String companyId) {
        log.info("Fetching HR users from user-service for company: {}", companyId);

        try {
            List<UserServiceClient.UserInfo> users = userServiceClient.getUsersByCompanyId(companyId).getData();
            if (users == null) {
                return Collections.emptyList();
            }

            List<HRUserResponse> hrUsers = users.stream()
                    .filter(u -> "HR".equals(u.userRole()) || "HR_MANAGER".equals(u.userRole()))
                    .map(u -> HRUserResponse.builder()
                            .userId(u.userId())
                            .username(u.username())
                            .fullName(u.fullName())
                            .email(u.email())
                            .phoneNumber(u.phoneNumber())
                            .userRole(u.userRole())
                            .userStatus(u.userStatus())
                            .companyId(u.companyId() != null ? u.companyId() : companyId)
                            .companyName(u.companyName())
                            .companyNo(u.companyNo())
                            .department(u.department())
                            .address(u.address())
                            .createdBy(u.createdBy())
                            .createdDate(u.createdDate())
                            .build())
                    .collect(Collectors.toList());

            log.info("Found {} HR users for company: {}", hrUsers.size(), companyId);
            return hrUsers;

        } catch (FeignException e) {
            log.error("Failed to fetch HR users from user-service for company {}: status={}", companyId, e.status());
            throw new org.workfitai.applicationservice.exception.BadRequestException(
                    "Unable to retrieve HR users. User service is unavailable.");
        }
    }

    // Get audit activities for all HR users in a company.
    public Page<HRAuditActivityResponse> getCompanyHRAuditActivities(
            String companyId,
            Instant fromDate,
            Instant toDate,
            Pageable pageable) {

        log.info("Fetching HR audit activities for company: {}, fromDate: {}, toDate: {}",
                companyId, fromDate, toDate);

        List<String> hrUsernames = getDistinctHRUsernamesForCompany(companyId);

        if (hrUsernames.isEmpty()) {
            log.info("No HR users found for company: {}", companyId);
            return Page.empty(pageable);
        }

        log.debug("Querying audit logs for {} HR users", hrUsernames.size());

        Page<AuditLog> auditLogs = (fromDate != null && toDate != null)
                ? auditLogRepository.findByPerformedByInAndPerformedAtBetweenOrderByPerformedAtDesc(
                        hrUsernames, fromDate, toDate, pageable)
                : auditLogRepository.findByPerformedByInOrderByPerformedAtDesc(hrUsernames, pageable);

        List<HRAuditActivityResponse> activities = auditLogs.getContent().stream()
                .map(auditLog -> HRAuditActivityResponse.builder()
                        .id(auditLog.getId())
                        .entityType(auditLog.getEntityType())
                        .entityId(auditLog.getEntityId())
                        .action(auditLog.getAction())
                        .performedBy(auditLog.getPerformedBy())
                        .performedAt(auditLog.getPerformedAt())
                        .metadata(auditLog.getMetadata())
                        .performerFullName(auditLog.getPerformedBy())
                        .performerRole("HR")
                        .build())
                .collect(Collectors.toList());

        log.info("Found {} audit activities for company HR users", activities.size());
        return new PageImpl<>(activities, pageable, auditLogs.getTotalElements());
    }

    /**
     * Returns distinct HR usernames (assignedTo + assignedBy) for a company using
     * MongoDB distinct queries — avoids loading full application documents.
     */
    private List<String> getDistinctHRUsernamesForCompany(String companyId) {
        Criteria companyCriteria = Criteria.where("companyId").is(companyId);

        List<String> assignedTo = mongoTemplate.findDistinct(
                Query.query(companyCriteria.and("assignedTo").exists(true).ne(null)),
                "assignedTo", Application.class, String.class);

        // Re-build criteria to avoid chaining issues with the same field
        List<String> assignedBy = mongoTemplate.findDistinct(
                Query.query(Criteria.where("companyId").is(companyId).and("assignedBy").exists(true).ne(null)),
                "assignedBy", Application.class, String.class);

        Set<String> combined = new LinkedHashSet<>();
        combined.addAll(assignedTo);
        combined.addAll(assignedBy);
        return new ArrayList<>(combined);
    }
}
