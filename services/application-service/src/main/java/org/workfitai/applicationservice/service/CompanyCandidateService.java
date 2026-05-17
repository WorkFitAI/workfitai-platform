package org.workfitai.applicationservice.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bson.Document;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.workfitai.applicationservice.client.UserServiceClient;
import org.workfitai.applicationservice.dto.response.CandidateProfileResponse;
import org.workfitai.applicationservice.dto.response.CandidateSummaryResponse;
import org.workfitai.applicationservice.dto.response.CompanyJobSummaryResponse;
import org.workfitai.applicationservice.dto.response.ResultPaginationDTO;
import org.workfitai.applicationservice.exception.NotFoundException;
import org.workfitai.applicationservice.mapper.ApplicationMapper;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.repository.ApplicationRepository;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Provides candidate and job views derived entirely from the Application collection.
 * Used by HRM (company-scoped) and HR (JWT-scoped) controllers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyCandidateService {

    private final MongoTemplate mongoTemplate;
    private final UserServiceClient userServiceClient;
    private final ApplicationMapper applicationMapper;
    private final ApplicationRepository applicationRepository;

    // ── Candidate List ───────────────────────────────────────────────────────

    public ResultPaginationDTO<CandidateSummaryResponse> getCandidateList(
            String companyId, String search, ApplicationStatus status, Pageable pageable) {

        log.info("Candidate list: companyId={}, search={}, status={}", companyId, search, status);

        Criteria criteria = buildCriteria(companyId, status, search);

        long total = countDistinctCandidates(criteria);
        if (total == 0) {
            return toPageResult(Collections.emptyList(), 0, pageable);
        }

        List<Document> rows = mongoTemplate.aggregate(
            Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.sort(Sort.Direction.DESC, "createdAt"),
                Aggregation.group("username")
                    .first("email").as("email")
                    .count().as("applicationCount")
                    .first("status").as("latestStatus")
                    .first("createdAt").as("latestApplicationDate")
                    .addToSet("jobSnapshot.title").as("appliedJobTitles"),
                Aggregation.skip((long) pageable.getPageNumber() * pageable.getPageSize()),
                Aggregation.limit(pageable.getPageSize())
            ),
            Application.class, Document.class
        ).getMappedResults();

        List<String> usernames = rows.stream().map(d -> d.getString("_id")).toList();
        Map<String, UserServiceClient.UserInfo> userMap = fetchUsersBatch(usernames);

        List<CandidateSummaryResponse> items = rows.stream().map(doc -> {
            String username = doc.getString("_id");
            UserServiceClient.UserInfo info = userMap.get(username);
            @SuppressWarnings("unchecked")
            List<String> titles = (List<String>) doc.get("appliedJobTitles");
            return CandidateSummaryResponse.builder()
                    .username(username)
                    .email(doc.getString("email"))
                    .fullName(info != null ? info.fullName() : null)
                    .phoneNumber(info != null ? info.phoneNumber() : null)
                    .userStatus(info != null ? info.userStatus() : null)
                    .applicationCount(((Number) doc.get("applicationCount")).longValue())
                    .latestStatus(ApplicationStatus.valueOf(doc.getString("latestStatus")))
                    .latestApplicationDate(
                            doc.getDate("latestApplicationDate") != null
                                    ? doc.getDate("latestApplicationDate").toInstant() : null)
                    .appliedJobTitles(titles != null ? titles.stream().filter(t -> t != null).toList() : List.of())
                    .build();
        }).toList();

        return toPageResult(items, total, pageable);
    }

    // ── Candidate Profile ────────────────────────────────────────────────────

    public CandidateProfileResponse getCandidateProfile(String companyId, String username) {
        log.info("Candidate profile: companyId={}, username={}", companyId, username);

        List<Application> apps = mongoTemplate.find(
                Query.query(Criteria.where("companyId").is(companyId)
                        .and("username").is(username)
                        .and("deletedAt").isNull())
                        .with(Sort.by(Sort.Direction.DESC, "createdAt")),
                Application.class);

        if (apps.isEmpty()) {
            throw new NotFoundException("No applications found for candidate: " + username);
        }

        UserServiceClient.UserInfo info = fetchUser(username);

        return CandidateProfileResponse.builder()
                .username(username)
                .userId(info != null ? info.userId() : null)
                .fullName(info != null ? info.fullName() : null)
                .email(apps.get(0).getEmail())
                .phoneNumber(info != null ? info.phoneNumber() : null)
                .userStatus(info != null ? info.userStatus() : null)
                .applications(apps.stream().map(applicationMapper::toResponse).toList())
                .totalApplications(apps.size())
                .build();
    }

    // ── Job List with Stats ──────────────────────────────────────────────────

    public ResultPaginationDTO<CompanyJobSummaryResponse> getJobsWithStats(
            String companyId, String jobTitle, Pageable pageable) {

        log.info("Jobs with stats: companyId={}, jobTitle={}", companyId, jobTitle);

        Criteria criteria = Criteria.where("companyId").is(companyId).and("deletedAt").isNull();
        if (jobTitle != null && !jobTitle.isBlank()) {
            criteria.and("jobSnapshot.title").regex(jobTitle.trim(), "i");
        }

        AggregationResults<Document> results = mongoTemplate.aggregate(
            Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.group("jobId")
                    .first("jobSnapshot.title").as("title")
                    .first("jobSnapshot.shortDescription").as("shortDescription")
                    .first("jobSnapshot.location").as("location")
                    .first("jobSnapshot.employmentType").as("employmentType")
                    .first("jobSnapshot.experienceLevel").as("experienceLevel")
                    .first("jobSnapshot.salaryMin").as("salaryMin")
                    .first("jobSnapshot.salaryMax").as("salaryMax")
                    .first("jobSnapshot.currency").as("currency")
                    .first("jobSnapshot.expiresAt").as("expiresAt")
                    .first("jobSnapshot.status").as("jobStatus")
                    .first("jobSnapshot.skillNames").as("skillNames")
                    .count().as("totalApplicants")
                    .push("status").as("statuses")
            ),
            Application.class, Document.class
        );

        List<Document> allRows = results.getMappedResults();
        long total = allRows.size();

        List<Document> pageRows = allRows.stream()
                .skip((long) pageable.getPageNumber() * pageable.getPageSize())
                .limit(pageable.getPageSize())
                .toList();

        List<CompanyJobSummaryResponse> items = pageRows.stream().map(doc -> {
            @SuppressWarnings("unchecked")
            List<String> statuses = (List<String>) doc.get("statuses");
            Map<String, Long> breakdown = statuses == null ? Map.of()
                    : statuses.stream().filter(s -> s != null)
                              .collect(Collectors.groupingBy(s -> s, Collectors.counting()));

            return CompanyJobSummaryResponse.builder()
                    .jobId(doc.getString("_id"))
                    .title(doc.getString("title"))
                    .shortDescription(doc.getString("shortDescription"))
                    .location(doc.getString("location"))
                    .employmentType(doc.getString("employmentType"))
                    .experienceLevel(doc.getString("experienceLevel"))
                    .salaryMin(doc.get("salaryMin") != null ? ((Number) doc.get("salaryMin")).doubleValue() : null)
                    .salaryMax(doc.get("salaryMax") != null ? ((Number) doc.get("salaryMax")).doubleValue() : null)
                    .currency(doc.getString("currency"))
                    .expiresAt(doc.getDate("expiresAt") != null ? doc.getDate("expiresAt").toInstant() : null)
                    .jobStatus(doc.getString("jobStatus"))
                    .skillNames((List<String>) doc.get("skillNames"))
                    .totalApplicants(((Number) doc.get("totalApplicants")).longValue())
                    .statusBreakdown(breakdown)
                    .build();
        }).toList();

        return toPageResult(items, total, pageable);
    }

    // ── Private Helpers ──────────────────────────────────────────────────────

    private Criteria buildCriteria(String companyId, ApplicationStatus status, String search) {
        Criteria c = Criteria.where("companyId").is(companyId).and("deletedAt").isNull();
        if (status != null) c.and("status").is(status);
        if (search != null && !search.isBlank()) {
            String regex = search.trim();
            c.orOperator(
                Criteria.where("username").regex(regex, "i"),
                Criteria.where("email").regex(regex, "i")
            );
        }
        return c;
    }

    private long countDistinctCandidates(Criteria criteria) {
        List<Document> countResult = mongoTemplate.aggregate(
            Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.group("username"),
                Aggregation.count().as("total")
            ),
            Application.class, Document.class
        ).getMappedResults();
        return countResult.isEmpty() ? 0 : ((Number) countResult.get(0).get("total")).longValue();
    }

    private Map<String, UserServiceClient.UserInfo> fetchUsersBatch(List<String> usernames) {
        if (usernames.isEmpty()) return Map.of();
        try {
            List<UserServiceClient.UserInfo> users = userServiceClient.getUsersByUsernames(usernames).getData();
            if (users == null) return Map.of();
            return users.stream().collect(Collectors.toMap(UserServiceClient.UserInfo::username, u -> u));
        } catch (FeignException e) {
            log.warn("user-service unavailable for batch fetch, returning partial data: {}", e.getMessage());
            return Map.of();
        }
    }

    private UserServiceClient.UserInfo fetchUser(String username) {
        try {
            return userServiceClient.getUserByUsername(username).getData();
        } catch (FeignException e) {
            log.warn("user-service unavailable for username={}: {}", username, e.getMessage());
            return null;
        }
    }

    private <T> ResultPaginationDTO<T> toPageResult(List<T> items, long total, Pageable pageable) {
        int page = pageable.getPageNumber();
        int size = pageable.getPageSize();
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) total / size);
        return ResultPaginationDTO.<T>builder()
                .items(items)
                .meta(ResultPaginationDTO.Meta.builder()
                        .page(page)
                        .size(size)
                        .totalElements(total)
                        .totalPages(totalPages)
                        .first(page == 0)
                        .last(page >= totalPages - 1)
                        .hasNext(page < totalPages - 1)
                        .hasPrevious(page > 0)
                        .build())
                .build();
    }
}
