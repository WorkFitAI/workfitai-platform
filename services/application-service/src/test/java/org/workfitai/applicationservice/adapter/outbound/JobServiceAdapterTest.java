package org.workfitai.applicationservice.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workfitai.applicationservice.client.JobServiceClient;
import org.workfitai.applicationservice.dto.JobInfo;
import org.workfitai.applicationservice.dto.response.RestResponse;
import org.workfitai.applicationservice.exception.NotFoundException;

import feign.FeignException;
import feign.Request;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

@ExtendWith(MockitoExtension.class)
class JobServiceAdapterTest {

    @Mock JobServiceClient jobServiceClient;

    @InjectMocks JobServiceAdapter adapter;

    private Map<String, Object> publishedJob() {
        Map<String, Object> job = new HashMap<>();
        job.put("postId", "post-1");
        job.put("title", "Java Developer");
        job.put("status", "PUBLISHED");
        job.put("description", "desc");
        job.put("shortDescription", "short desc");
        job.put("employmentType", "FULL_TIME");
        job.put("experienceLevel", "MID");
        job.put("educationLevel", "BACHELOR");
        job.put("requiredExperience", "3 years");
        job.put("salaryMin", 1000.0);
        job.put("salaryMax", 2000.0);
        job.put("currency", "VND");
        job.put("location", "HCM");
        job.put("quantity", 2);
        job.put("totalApplications", 5);
        job.put("createdDate", "2024-01-01T00:00:00Z");
        job.put("lastModifiedDate", "2024-06-01T00:00:00Z");
        job.put("bannerUrl", null);
        job.put("createdBy", "admin");

        Map<String, Object> company = new HashMap<>();
        company.put("name", "Acme Corp");
        company.put("companyNo", "COMP-001");
        company.put("description", "Tech company");
        company.put("address", "HCM City");
        company.put("websiteUrl", "https://acme.com");
        company.put("logoUrl", "https://acme.com/logo.png");
        company.put("size", "100-200");
        job.put("company", company);
        job.put("skillNames", java.util.List.of("Java", "Spring"));
        return job;
    }

    private Request dummyRequest() {
        return Request.create(Request.HttpMethod.GET, "http://job/id",
                Collections.emptyMap(), null, StandardCharsets.UTF_8, null);
    }

    // ─── validateAndGetJob ────────────────────────────────────────────────────

    @Test
    void validateAndGetJob_publishedJob_returnsJobInfo() {
        when(jobServiceClient.getJobById("job-1"))
                .thenReturn(new RestResponse<>(200, "OK", publishedJob()));

        JobInfo result = adapter.validateAndGetJob("job-1");

        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("Java Developer");
        assertThat(result.getCompanyName()).isEqualTo("Acme Corp");
        assertThat(result.getStatus()).isEqualTo("PUBLISHED");
        assertThat(result.getSalaryMin()).isEqualTo(1000.0);
    }

    @Test
    void validateAndGetJob_nullResponse_throwsNotFoundException() {
        when(jobServiceClient.getJobById("job-1"))
                .thenReturn(new RestResponse<>(200, "OK", null));

        assertThatThrownBy(() -> adapter.validateAndGetJob("job-1"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("job-1");
    }

    @Test
    void validateAndGetJob_notPublished_throwsNotFoundException() {
        Map<String, Object> job = publishedJob();
        job.put("status", "DRAFT");
        when(jobServiceClient.getJobById("job-1"))
                .thenReturn(new RestResponse<>(200, "OK", job));

        assertThatThrownBy(() -> adapter.validateAndGetJob("job-1"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("not available");
    }

    @Test
    void validateAndGetJob_expired_throwsNotFoundException() {
        Map<String, Object> job = publishedJob();
        job.put("expiresAt", "2020-01-01T00:00:00Z");
        when(jobServiceClient.getJobById("job-1"))
                .thenReturn(new RestResponse<>(200, "OK", job));

        assertThatThrownBy(() -> adapter.validateAndGetJob("job-1"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void validateAndGetJob_feignNotFound_throwsNotFoundException() {
        when(jobServiceClient.getJobById("job-1"))
                .thenThrow(new FeignException.NotFound("404", dummyRequest(), null, Collections.emptyMap()));

        assertThatThrownBy(() -> adapter.validateAndGetJob("job-1"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void validateAndGetJob_feignError_throwsRuntimeException() {
        when(jobServiceClient.getJobById("job-1"))
                .thenThrow(new FeignException.ServiceUnavailable("503", dummyRequest(), null, Collections.emptyMap()));

        assertThatThrownBy(() -> adapter.validateAndGetJob("job-1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to validate job");
    }

    @Test
    void validateAndGetJob_futureExpiry_returnsJob() {
        Map<String, Object> job = publishedJob();
        job.put("expiresAt", "2099-12-31T00:00:00Z");
        when(jobServiceClient.getJobById("job-1"))
                .thenReturn(new RestResponse<>(200, "OK", job));

        JobInfo result = adapter.validateAndGetJob("job-1");

        assertThat(result).isNotNull();
    }

    @Test
    void validateAndGetJob_nullCompany_usesDefaults() {
        Map<String, Object> job = publishedJob();
        job.put("company", null);
        when(jobServiceClient.getJobById("job-1"))
                .thenReturn(new RestResponse<>(200, "OK", job));

        JobInfo result = adapter.validateAndGetJob("job-1");

        assertThat(result.getCompanyName()).isEqualTo("Unknown");
        assertThat(result.getCompanyId()).isNull();
    }

    // ─── jobExists ────────────────────────────────────────────────────────────

    @Test
    void jobExists_publishedJob_returnsTrue() {
        when(jobServiceClient.getJobById("job-1"))
                .thenReturn(new RestResponse<>(200, "OK", publishedJob()));

        assertThat(adapter.jobExists("job-1")).isTrue();
    }

    @Test
    void jobExists_nullResponse_returnsFalse() {
        when(jobServiceClient.getJobById("job-1"))
                .thenReturn(new RestResponse<>(200, "OK", null));

        assertThat(adapter.jobExists("job-1")).isFalse();
    }

    @Test
    void jobExists_notPublished_returnsFalse() {
        Map<String, Object> job = publishedJob();
        job.put("status", "CLOSED");
        when(jobServiceClient.getJobById("job-1"))
                .thenReturn(new RestResponse<>(200, "OK", job));

        assertThat(adapter.jobExists("job-1")).isFalse();
    }

    @Test
    void jobExists_expired_returnsFalse() {
        Map<String, Object> job = publishedJob();
        job.put("expiresAt", "2020-01-01T00:00:00Z");
        when(jobServiceClient.getJobById("job-1"))
                .thenReturn(new RestResponse<>(200, "OK", job));

        assertThat(adapter.jobExists("job-1")).isFalse();
    }

    @Test
    void jobExists_feignNotFound_returnsFalse() {
        when(jobServiceClient.getJobById("job-1"))
                .thenThrow(new FeignException.NotFound("404", dummyRequest(), null, Collections.emptyMap()));

        assertThat(adapter.jobExists("job-1")).isFalse();
    }

    @Test
    void jobExists_feignError_returnsFalse() {
        when(jobServiceClient.getJobById("job-1"))
                .thenThrow(new FeignException.ServiceUnavailable("503", dummyRequest(), null, Collections.emptyMap()));

        assertThat(adapter.jobExists("job-1")).isFalse();
    }
}
