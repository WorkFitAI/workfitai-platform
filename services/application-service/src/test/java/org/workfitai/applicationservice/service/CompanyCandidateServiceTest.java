package org.workfitai.applicationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;
import org.workfitai.applicationservice.client.UserServiceClient;
import org.workfitai.applicationservice.dto.response.CandidateProfileResponse;
import org.workfitai.applicationservice.dto.response.CandidateSummaryResponse;
import org.workfitai.applicationservice.dto.response.CompanyJobSummaryResponse;
import org.workfitai.applicationservice.dto.response.RestResponse;
import org.workfitai.applicationservice.dto.response.ResultPaginationDTO;
import org.workfitai.applicationservice.exception.NotFoundException;
import org.workfitai.applicationservice.mapper.ApplicationMapper;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.repository.ApplicationRepository;

import feign.FeignException;
import feign.Request;

import java.nio.charset.StandardCharsets;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompanyCandidateServiceTest {

    @Mock MongoTemplate mongoTemplate;
    @Mock UserServiceClient userServiceClient;
    @Mock ApplicationMapper applicationMapper;
    @Mock ApplicationRepository applicationRepository;

    @InjectMocks CompanyCandidateService service;

    private final Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));

    @SuppressWarnings("unchecked")
    private AggregationResults<Document> emptyAggResult() {
        return new AggregationResults<>(Collections.emptyList(), new Document());
    }

    @SuppressWarnings("unchecked")
    private AggregationResults<Document> aggResult(List<Document> docs) {
        return new AggregationResults<>(docs, new Document());
    }

    private Request dummyRequest() {
        return Request.create(Request.HttpMethod.GET, "http://user/api",
                Collections.emptyMap(), null, StandardCharsets.UTF_8, null);
    }

    // ── getCandidateList ─────────────────────────────────────────────────────

    @Test
    void getCandidateList_noApplications_returnsEmpty() {
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(Application.class), eq(Document.class)))
                .thenReturn(emptyAggResult());

        ResultPaginationDTO<CandidateSummaryResponse> result =
                service.getCandidateList("company-1", null, null, pageable);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getMeta().getTotalElements()).isEqualTo(0);
    }

    @Test
    void getCandidateList_withResults_mapsUserInfo() {
        // First aggregate call = countDistinctCandidates → returns [{total: 1}]
        Document countDoc = new Document("total", 1);
        // Second aggregate call = list rows
        Document row = new Document("_id", "user1")
                .append("email", "user1@test.com")
                .append("applicationCount", 2)
                .append("latestStatus", "APPLIED")
                .append("latestApplicationDate", new Date())
                .append("appliedJobTitles", List.of("Java Dev"));

        when(mongoTemplate.aggregate(any(Aggregation.class), eq(Application.class), eq(Document.class)))
                .thenReturn(aggResult(List.of(countDoc)))
                .thenReturn(aggResult(List.of(row)));

        UserServiceClient.UserInfo userInfo = new UserServiceClient.UserInfo(
                "uid-1", "user1", "John Doe", "user1@test.com", "+84123",
                "HR", "ACTIVE", "company-1", "Acme", "C001",
                null, null, null, null, null, null);
        RestResponse<List<UserServiceClient.UserInfo>> batchResponse = new RestResponse<>(200, "OK", List.of(userInfo));
        when(userServiceClient.getUsersByUsernames(any())).thenReturn(batchResponse);

        ResultPaginationDTO<CandidateSummaryResponse> result =
                service.getCandidateList("company-1", null, null, pageable);

        assertThat(result.getItems()).hasSize(1);
        CandidateSummaryResponse item = result.getItems().get(0);
        assertThat(item.getUsername()).isEqualTo("user1");
        assertThat(item.getFullName()).isEqualTo("John Doe");
        assertThat(item.getApplicationCount()).isEqualTo(2);
    }

    @Test
    void getCandidateList_userServiceUnavailable_returnsPartialData() {
        Document countDoc = new Document("total", 1);
        Document row = new Document("_id", "user1")
                .append("email", "u@test.com")
                .append("applicationCount", 1)
                .append("latestStatus", "APPLIED")
                .append("latestApplicationDate", (Date) null)
                .append("appliedJobTitles", (List<String>) null); // null titles

        when(mongoTemplate.aggregate(any(Aggregation.class), eq(Application.class), eq(Document.class)))
                .thenReturn(aggResult(List.of(countDoc)))
                .thenReturn(aggResult(List.of(row)));

        when(userServiceClient.getUsersByUsernames(any()))
                .thenThrow(new FeignException.ServiceUnavailable("503", dummyRequest(), null, Collections.emptyMap()));

        ResultPaginationDTO<CandidateSummaryResponse> result =
                service.getCandidateList("company-1", null, null, pageable);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getFullName()).isNull(); // no user data
    }

    @Test
    void getCandidateList_withStatusAndSearch_filtersApplied() {
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(Application.class), eq(Document.class)))
                .thenReturn(emptyAggResult());

        ResultPaginationDTO<CandidateSummaryResponse> result =
                service.getCandidateList("company-1", "john", ApplicationStatus.REVIEWING, pageable);

        assertThat(result.getItems()).isEmpty();
    }

    @Test
    void getCandidateList_batchFetchReturnsNullData_handlesGracefully() {
        Document countDoc = new Document("total", 1);
        Document row = new Document("_id", "user1")
                .append("email", "u@test.com")
                .append("applicationCount", 1)
                .append("latestStatus", "APPLIED")
                .append("latestApplicationDate", (Date) null)
                .append("appliedJobTitles", List.of("Dev"));

        when(mongoTemplate.aggregate(any(Aggregation.class), eq(Application.class), eq(Document.class)))
                .thenReturn(aggResult(List.of(countDoc)))
                .thenReturn(aggResult(List.of(row)));

        // getUsersByUsernames returns response with null data
        RestResponse<List<UserServiceClient.UserInfo>> nullDataResponse = new RestResponse<>(200, "OK", null);
        when(userServiceClient.getUsersByUsernames(any())).thenReturn(nullDataResponse);

        ResultPaginationDTO<CandidateSummaryResponse> result =
                service.getCandidateList("company-1", null, null, pageable);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getFullName()).isNull();
    }

    // ── getCandidateProfile ──────────────────────────────────────────────────

    @Test
    void getCandidateProfile_noApplications_throwsNotFoundException() {
        when(mongoTemplate.find(any(Query.class), eq(Application.class))).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> service.getCandidateProfile("company-1", "ghost"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    void getCandidateProfile_withApplications_returnsProfile() {
        Application app = Application.builder().id("a1").username("user1").email("u@test.com").build();
        when(mongoTemplate.find(any(Query.class), eq(Application.class))).thenReturn(List.of(app));

        UserServiceClient.UserInfo info = new UserServiceClient.UserInfo(
                "uid-1", "user1", "Alice", "u@test.com", "+84",
                "HR", "ACTIVE", "company-1", "Acme", "C001",
                null, null, null, null, null, null);
        RestResponse<UserServiceClient.UserInfo> userResp = new RestResponse<>(200, "OK", info);
        when(userServiceClient.getUserByUsername("user1")).thenReturn(userResp);
        when(applicationMapper.toResponse(app)).thenReturn(new org.workfitai.applicationservice.dto.response.ApplicationResponse());

        CandidateProfileResponse result = service.getCandidateProfile("company-1", "user1");

        assertThat(result.getUsername()).isEqualTo("user1");
        assertThat(result.getFullName()).isEqualTo("Alice");
        assertThat(result.getTotalApplications()).isEqualTo(1);
    }

    @Test
    void getCandidateProfile_userServiceFails_returnsProfileWithNullUserInfo() {
        Application app = Application.builder().id("a1").username("user1").email("u@test.com").build();
        when(mongoTemplate.find(any(Query.class), eq(Application.class))).thenReturn(List.of(app));
        when(userServiceClient.getUserByUsername("user1"))
                .thenThrow(new FeignException.NotFound("404", dummyRequest(), null, Collections.emptyMap()));
        when(applicationMapper.toResponse(app)).thenReturn(new org.workfitai.applicationservice.dto.response.ApplicationResponse());

        CandidateProfileResponse result = service.getCandidateProfile("company-1", "user1");

        assertThat(result.getFullName()).isNull();
        assertThat(result.getUserId()).isNull();
    }

    // ── getJobsWithStats ─────────────────────────────────────────────────────

    @Test
    void getJobsWithStats_noJobs_returnsEmpty() {
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(Application.class), eq(Document.class)))
                .thenReturn(emptyAggResult());

        ResultPaginationDTO<CompanyJobSummaryResponse> result =
                service.getJobsWithStats("company-1", null, pageable);

        assertThat(result.getItems()).isEmpty();
    }

    @Test
    void getJobsWithStats_withJobTitle_appliesTitleFilter() {
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(Application.class), eq(Document.class)))
                .thenReturn(emptyAggResult());

        ResultPaginationDTO<CompanyJobSummaryResponse> result =
                service.getJobsWithStats("company-1", "Java", pageable);

        assertThat(result.getItems()).isEmpty();
    }

    @Test
    void getJobsWithStats_withJobs_mapsBreakdown() {
        Document jobDoc = new Document("_id", "job-1")
                .append("title", "Java Dev")
                .append("shortDescription", "desc")
                .append("location", "HCM")
                .append("employmentType", "FULL_TIME")
                .append("experienceLevel", "MID")
                .append("salaryMin", 1000.0)
                .append("salaryMax", 2000.0)
                .append("currency", "VND")
                .append("expiresAt", new Date())
                .append("jobStatus", "PUBLISHED")
                .append("skillNames", List.of("Java"))
                .append("totalApplicants", 3)
                .append("statuses", List.of("APPLIED", "REVIEWING", "APPLIED"));

        when(mongoTemplate.aggregate(any(Aggregation.class), eq(Application.class), eq(Document.class)))
                .thenReturn(aggResult(List.of(jobDoc)));

        ResultPaginationDTO<CompanyJobSummaryResponse> result =
                service.getJobsWithStats("company-1", null, pageable);

        assertThat(result.getItems()).hasSize(1);
        CompanyJobSummaryResponse item = result.getItems().get(0);
        assertThat(item.getJobId()).isEqualTo("job-1");
        assertThat(item.getStatusBreakdown()).containsKey("APPLIED");
        assertThat(item.getStatusBreakdown().get("APPLIED")).isEqualTo(2L);
    }

    @Test
    void getJobsWithStats_nullSalaryAndExpiry_handlesNulls() {
        Document jobDoc = new Document("_id", "job-2")
                .append("title", "Go Dev")
                .append("totalApplicants", 1)
                .append("statuses", (List<String>) null) // null statuses
                .append("salaryMin", (Double) null)
                .append("salaryMax", (Double) null)
                .append("expiresAt", (Date) null);

        when(mongoTemplate.aggregate(any(Aggregation.class), eq(Application.class), eq(Document.class)))
                .thenReturn(aggResult(List.of(jobDoc)));

        ResultPaginationDTO<CompanyJobSummaryResponse> result =
                service.getJobsWithStats("company-1", null, pageable);

        assertThat(result.getItems()).hasSize(1);
        CompanyJobSummaryResponse item = result.getItems().get(0);
        assertThat(item.getSalaryMin()).isNull();
        assertThat(item.getExpiresAt()).isNull();
        assertThat(item.getStatusBreakdown()).isEmpty();
    }
}
