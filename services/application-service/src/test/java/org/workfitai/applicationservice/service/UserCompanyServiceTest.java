package org.workfitai.applicationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workfitai.applicationservice.client.UserServiceClient;
import org.workfitai.applicationservice.dto.response.RestResponse;
import org.workfitai.applicationservice.exception.BadRequestException;

import feign.FeignException;
import feign.Request;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class UserCompanyServiceTest {

    @Mock UserServiceClient userServiceClient;

    @InjectMocks UserCompanyService userCompanyService;

    private UserServiceClient.UserInfo userInfo(String username, String companyId) {
        return new UserServiceClient.UserInfo(
                "uid-1", username, "Full Name", "email@test.com",
                null, "HR", "ACTIVE", companyId, "Acme", null,
                null, null, null, null, null, null);
    }

    private RestResponse<UserServiceClient.UserInfo> wrap(UserServiceClient.UserInfo info) {
        return new RestResponse<>(200, "OK", info);
    }

    // ─── cache miss → Feign call ──────────────────────────────────────────────

    @Test
    void getUserCompanyId_cacheMiss_fetchesFromUserService() {
        when(userServiceClient.getUserByUsername("hr1")).thenReturn(wrap(userInfo("hr1", "company-1")));

        String result = userCompanyService.getUserCompanyId("hr1");

        assertThat(result).isEqualTo("company-1");
        verify(userServiceClient).getUserByUsername("hr1");
    }

    // ─── cache hit → no Feign call ────────────────────────────────────────────

    @Test
    void getUserCompanyId_cacheHit_doesNotCallFeign() {
        when(userServiceClient.getUserByUsername("hr1")).thenReturn(wrap(userInfo("hr1", "company-1")));

        userCompanyService.getUserCompanyId("hr1"); // warms cache
        userCompanyService.getUserCompanyId("hr1"); // cache hit

        verify(userServiceClient, times(1)).getUserByUsername("hr1");
    }

    // ─── null companyId → sentinel cached, returns null ───────────────────────

    @Test
    void getUserCompanyId_noCompanyId_returnsNull() {
        when(userServiceClient.getUserByUsername("candidate1")).thenReturn(wrap(userInfo("candidate1", null)));

        String result = userCompanyService.getUserCompanyId("candidate1");

        assertThat(result).isNull();
    }

    @Test
    void getUserCompanyId_sentinelCached_subsequentCallDoesNotHitFeign() {
        when(userServiceClient.getUserByUsername("candidate1")).thenReturn(wrap(userInfo("candidate1", null)));

        userCompanyService.getUserCompanyId("candidate1"); // caches sentinel ""
        userCompanyService.getUserCompanyId("candidate1"); // cache hit

        verify(userServiceClient, times(1)).getUserByUsername("candidate1");
    }

    // ─── null userInfo → BadRequestException ─────────────────────────────────

    @Test
    void getUserCompanyId_nullUserInfo_throwsBadRequest() {
        when(userServiceClient.getUserByUsername("unknown")).thenReturn(new RestResponse<>(200, "OK", null));

        assertThatThrownBy(() -> userCompanyService.getUserCompanyId("unknown"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("unknown");
    }

    // ─── FeignException.NotFound ──────────────────────────────────────────────

    @Test
    void getUserCompanyId_feignNotFound_throwsBadRequest() {
        Request dummyReq = Request.create(Request.HttpMethod.GET, "http://user/by-username",
                Map.of(), null, StandardCharsets.UTF_8, null);
        when(userServiceClient.getUserByUsername("ghost"))
                .thenThrow(new FeignException.NotFound("Not found", dummyReq,
                        null, Map.of()));

        assertThatThrownBy(() -> userCompanyService.getUserCompanyId("ghost"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ghost");
    }

    // ─── FeignException (non-404) ─────────────────────────────────────────────

    @Test
    void getUserCompanyId_feignServiceError_throwsBadRequest() {
        Request dummyReq = Request.create(Request.HttpMethod.GET, "http://user/by-username",
                Map.of(), null, StandardCharsets.UTF_8, null);
        when(userServiceClient.getUserByUsername("hr2"))
                .thenThrow(new FeignException.ServiceUnavailable("503", dummyReq,
                        null, Map.of()));

        assertThatThrownBy(() -> userCompanyService.getUserCompanyId("hr2"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Failed to fetch");
    }

    // ─── evictCache ───────────────────────────────────────────────────────────

    @Test
    void evictCache_afterEvict_nextCallHitsFeign() {
        when(userServiceClient.getUserByUsername("hr1")).thenReturn(wrap(userInfo("hr1", "company-1")));

        userCompanyService.getUserCompanyId("hr1"); // warms cache
        userCompanyService.evictCache("hr1");
        userCompanyService.getUserCompanyId("hr1"); // should hit Feign again

        verify(userServiceClient, times(2)).getUserByUsername("hr1");
    }

    // ─── clearCache ───────────────────────────────────────────────────────────

    @Test
    void clearCache_resetsAllEntries() {
        when(userServiceClient.getUserByUsername("hr1")).thenReturn(wrap(userInfo("hr1", "company-1")));
        when(userServiceClient.getUserByUsername("hr2")).thenReturn(wrap(userInfo("hr2", "company-2")));

        userCompanyService.getUserCompanyId("hr1");
        userCompanyService.getUserCompanyId("hr2");
        userCompanyService.clearCache();
        userCompanyService.getUserCompanyId("hr1");
        userCompanyService.getUserCompanyId("hr2");

        verify(userServiceClient, times(2)).getUserByUsername("hr1");
        verify(userServiceClient, times(2)).getUserByUsername("hr2");
    }

    // ─── getCacheSize ─────────────────────────────────────────────────────────

    @Test
    void getCacheSize_afterOneEntry_returnsPositive() {
        when(userServiceClient.getUserByUsername("hr1")).thenReturn(wrap(userInfo("hr1", "company-1")));
        userCompanyService.getUserCompanyId("hr1");

        assertThat(userCompanyService.getCacheSize()).isGreaterThan(0);
    }
}
