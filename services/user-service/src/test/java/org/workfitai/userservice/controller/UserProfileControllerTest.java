package org.workfitai.userservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.workfitai.userservice.dto.request.AdminUpdateRequest;
import org.workfitai.userservice.dto.request.CandidateUpdateRequest;
import org.workfitai.userservice.dto.request.DeactivateAccountRequest;
import org.workfitai.userservice.dto.request.DeleteAccountRequest;
import org.workfitai.userservice.dto.request.HRUpdateRequest;
import org.workfitai.userservice.dto.response.AccountManagementResponse;
import org.workfitai.userservice.dto.response.AdminResponse;
import org.workfitai.userservice.dto.response.CandidateResponse;
import org.workfitai.userservice.dto.response.HRResponse;
import org.workfitai.userservice.dto.response.ResponseData;
import org.workfitai.userservice.service.AccountManagementService;
import org.workfitai.userservice.service.AdminService;
import org.workfitai.userservice.service.CandidateService;
import org.workfitai.userservice.service.HRService;
import org.workfitai.userservice.service.UserService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserProfileControllerTest {

    @Mock UserService userService;
    @Mock CandidateService candidateService;
    @Mock HRService hrService;
    @Mock AdminService adminService;
    @Mock AccountManagementService accountManagementService;
    @InjectMocks UserProfileController controller;

    private Authentication auth;
    private static final String USERNAME = "testuser";

    @BeforeEach
    void setup() {
        auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(USERNAME);
        when(auth.getPrincipal()).thenReturn(USERNAME);
        when(auth.getDetails()).thenReturn(null);
    }

    @Test
    void getMyProfile_delegatesToUserService() {
        Object profile = new CandidateResponse();
        when(userService.getCurrentUserProfileByUsername(USERNAME)).thenReturn(profile);

        ResponseEntity<ResponseData<Object>> resp = controller.getMyProfile(USERNAME);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getData()).isEqualTo(profile);
        verify(userService).getCurrentUserProfileByUsername(USERNAME);
    }

    @Test
    void updateCandidateProfile_delegatesToCandidateService() {
        UUID id = UUID.randomUUID();
        CandidateUpdateRequest req = new CandidateUpdateRequest();
        CandidateResponse updated = new CandidateResponse();
        when(userService.findUserIdByUsername(USERNAME)).thenReturn(id);
        when(candidateService.update(id, req)).thenReturn(updated);

        ResponseEntity<ResponseData<CandidateResponse>> resp = controller.updateCandidateProfile(USERNAME, req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getData()).isEqualTo(updated);
        verify(candidateService).update(id, req);
    }

    @Test
    void updateHRProfile_delegatesToHRService() {
        UUID id = UUID.randomUUID();
        HRUpdateRequest req = new HRUpdateRequest();
        HRResponse updated = new HRResponse();
        when(userService.findUserIdByUsername(USERNAME)).thenReturn(id);
        when(hrService.update(id, req)).thenReturn(updated);

        ResponseEntity<ResponseData<HRResponse>> resp = controller.updateHRProfile(USERNAME, req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(hrService).update(id, req);
    }

    @Test
    void updateAdminProfile_delegatesToAdminService() {
        UUID id = UUID.randomUUID();
        AdminUpdateRequest req = new AdminUpdateRequest();
        AdminResponse updated = new AdminResponse();
        when(userService.findUserIdByUsername(USERNAME)).thenReturn(id);
        when(adminService.update(id, req)).thenReturn(updated);

        ResponseEntity<ResponseData<AdminResponse>> resp = controller.updateAdminProfile(USERNAME, req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(adminService).update(id, req);
    }

    @Test
    void deactivateAccount_delegatesToAccountManagementService() {
        DeactivateAccountRequest req = new DeactivateAccountRequest();
        AccountManagementResponse response = AccountManagementResponse.builder()
                .status("DEACTIVATED").message("deactivated").build();
        when(accountManagementService.deactivateAccount(USERNAME, req)).thenReturn(response);

        ResponseEntity<AccountManagementResponse> resp = controller.deactivateAccount(req, auth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getStatus()).isEqualTo("DEACTIVATED");
        verify(accountManagementService).deactivateAccount(USERNAME, req);
    }

    @Test
    void requestAccountDeletion_delegatesToAccountManagementService() {
        DeleteAccountRequest req = new DeleteAccountRequest();
        AccountManagementResponse response = AccountManagementResponse.builder()
                .status("PENDING_DELETION").message("scheduled").build();
        when(accountManagementService.requestAccountDeletion(USERNAME, req)).thenReturn(response);

        ResponseEntity<AccountManagementResponse> resp = controller.requestAccountDeletion(req, auth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(accountManagementService).requestAccountDeletion(USERNAME, req);
    }

    @Test
    void cancelAccountDeletion_delegatesToAccountManagementService() {
        AccountManagementResponse response = AccountManagementResponse.builder()
                .status("ACTIVE").message("cancelled").build();
        when(accountManagementService.cancelAccountDeletion(USERNAME)).thenReturn(response);

        ResponseEntity<AccountManagementResponse> resp = controller.cancelAccountDeletion(auth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(accountManagementService).cancelAccountDeletion(USERNAME);
    }
}
