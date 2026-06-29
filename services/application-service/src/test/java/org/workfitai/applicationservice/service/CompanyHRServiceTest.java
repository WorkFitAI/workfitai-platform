package org.workfitai.applicationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.workfitai.applicationservice.client.UserServiceClient;
import org.workfitai.applicationservice.dto.response.HRUserResponse;
import org.workfitai.applicationservice.dto.response.RestResponse;
import org.workfitai.applicationservice.exception.BadRequestException;

import feign.FeignException;
import feign.Request;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class CompanyHRServiceTest {

    @Mock MongoTemplate mongoTemplate;
    @Mock UserServiceClient userServiceClient;

    @InjectMocks CompanyHRService service;

    private UserServiceClient.UserInfo hrUser(String username, String role) {
        return new UserServiceClient.UserInfo(
                "uid-1", username, "Full Name", "email@test.com",
                "0123456789", role, "ACTIVE", "company-1",
                "Acme Corp", "C001", "Engineering", "HCM",
                "admin", null, null, null);
    }

    private RestResponse<List<UserServiceClient.UserInfo>> wrap(List<UserServiceClient.UserInfo> users) {
        return new RestResponse<>(200, "OK", users);
    }

    // ─── getCompanyHRUsers ────────────────────────────────────────────────────

    @Test
    void getCompanyHRUsers_filtersOnlyHrRoles() {
        List<UserServiceClient.UserInfo> users = List.of(
                hrUser("hr1", "HR"),
                hrUser("hrm1", "HR_MANAGER"),
                hrUser("candidate1", "CANDIDATE") // should be filtered out
        );
        when(userServiceClient.getUsersByCompanyId("company-1")).thenReturn(wrap(users));

        List<HRUserResponse> result = service.getCompanyHRUsers("company-1");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(HRUserResponse::username)
                .containsExactlyInAnyOrder("hr1", "hrm1");
    }

    @Test
    void getCompanyHRUsers_nullData_returnsEmptyList() {
        when(userServiceClient.getUsersByCompanyId("company-1")).thenReturn(wrap(null));

        List<HRUserResponse> result = service.getCompanyHRUsers("company-1");

        assertThat(result).isEmpty();
    }

    @Test
    void getCompanyHRUsers_emptyList_returnsEmpty() {
        when(userServiceClient.getUsersByCompanyId("company-1")).thenReturn(wrap(Collections.emptyList()));

        List<HRUserResponse> result = service.getCompanyHRUsers("company-1");

        assertThat(result).isEmpty();
    }

    @Test
    void getCompanyHRUsers_feignException_throwsBadRequest() {
        Request req = Request.create(Request.HttpMethod.GET, "http://user/by-company",
                Map.of(), null, StandardCharsets.UTF_8, null);
        when(userServiceClient.getUsersByCompanyId("company-1"))
                .thenThrow(new FeignException.ServiceUnavailable("503", req, null, Map.of()));

        assertThatThrownBy(() -> service.getCompanyHRUsers("company-1"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("unavailable");
    }

    @Test
    void getCompanyHRUsers_userWithNullCompanyId_usesProvidedCompanyId() {
        UserServiceClient.UserInfo user = new UserServiceClient.UserInfo(
                "uid-1", "hr1", "Full Name", "email@test.com",
                null, "HR", "ACTIVE", null, // companyId is null
                null, null, null, null, null, null, null, null);
        when(userServiceClient.getUsersByCompanyId("company-1")).thenReturn(wrap(List.of(user)));

        List<HRUserResponse> result = service.getCompanyHRUsers("company-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).companyId()).isEqualTo("company-1");
    }

    // ─── getCompanyHRAuditActivities (deprecated stub) ───────────────────────

    @Test
    void getCompanyHRAuditActivities_deprecated_returnsEmptyPage() {
        Page<?> result = service.getCompanyHRAuditActivities(
                "company-1", null, null, PageRequest.of(0, 10));

        assertThat(result).isEmpty();
    }
}
