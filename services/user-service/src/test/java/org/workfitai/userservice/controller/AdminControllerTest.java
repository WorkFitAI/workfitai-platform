package org.workfitai.userservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.workfitai.userservice.dto.request.AdminCreateRequest;
import org.workfitai.userservice.dto.request.AdminUpdateRequest;
import org.workfitai.userservice.dto.response.AdminResponse;
import org.workfitai.userservice.dto.response.ResponseData;
import org.workfitai.userservice.dto.response.UserBaseResponse;
import org.workfitai.userservice.exception.ApiException;
import org.workfitai.userservice.service.AdminService;
import org.workfitai.userservice.service.PlatformFeatureToggleService;
import org.workfitai.userservice.service.UserIndexManagementService;
import org.workfitai.userservice.service.UserSearchService;
import org.workfitai.userservice.service.UserService;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock AdminService adminService;
    @Mock UserService userService;
    @Mock UserSearchService userSearchService;
    @Mock UserIndexManagementService indexManagementService;
    @Mock PlatformFeatureToggleService featureToggleService;

    @InjectMocks
    AdminController controller;

    private UUID adminId;
    private AdminResponse adminResponse;

    @BeforeEach
    void setUp() {
        adminId = UUID.randomUUID();
        adminResponse = new AdminResponse();
        adminResponse.setFullName("Admin User");
        adminResponse.setEmail("admin@test.com");
    }

    // ---- create ----

    @Test
    void create_returnsCreatedAdmin() {
        AdminCreateRequest req = new AdminCreateRequest();
        when(adminService.create(req)).thenReturn(adminResponse);

        ResponseEntity<ResponseData<AdminResponse>> resp = controller.create(req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getData()).isEqualTo(adminResponse);
        verify(adminService).create(req);
    }

    // ---- update ----

    @Test
    void update_returnsUpdatedAdmin() {
        AdminUpdateRequest req = new AdminUpdateRequest();
        when(adminService.update(adminId, req)).thenReturn(adminResponse);

        ResponseEntity<ResponseData<AdminResponse>> resp = controller.update(adminId, req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getData()).isEqualTo(adminResponse);
    }

    // ---- delete ----

    @Test
    void delete_callsServiceAndReturnsOk() {
        doNothing().when(adminService).delete(adminId);

        ResponseEntity<ResponseData<Void>> resp = controller.delete(adminId);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(adminService).delete(adminId);
    }

    // ---- getById ----

    @Test
    void getById_returnsAdmin() {
        when(adminService.getById(adminId)).thenReturn(adminResponse);

        ResponseEntity<ResponseData<AdminResponse>> resp = controller.getById(adminId);

        assertThat(resp.getBody().getData()).isEqualTo(adminResponse);
    }

    // ---- search ----

    @Test
    void search_returnsPage() {
        Page<AdminResponse> page = new PageImpl<>(List.of(adminResponse));
        when(adminService.search(null, Pageable.unpaged())).thenReturn(page);

        ResponseEntity<ResponseData<Page<AdminResponse>>> resp =
                controller.search(null, Pageable.unpaged());

        assertThat(resp.getBody().getData().getContent()).hasSize(1);
    }

    // ---- getAllUsers ----

    @Test
    void getAllUsers_delegatesToUserService() {
        Page<UserBaseResponse> users = new PageImpl<>(List.of(new UserBaseResponse()));
        when(userService.searchAllUsers(null, null, Pageable.unpaged())).thenReturn(users);

        ResponseEntity<ResponseData<Page<UserBaseResponse>>> resp =
                controller.getAllUsers(null, null, Pageable.unpaged());

        assertThat(resp.getBody().getData().getTotalElements()).isEqualTo(1);
    }

    // ---- blockUser ----

    @Test
    void blockUser_selfBlock_throwsApiException() {
        String userId = adminId.toString();

        assertThatThrownBy(() -> controller.blockUser(adminId, true, userId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cannot block yourself");
    }

    @Test
    void blockUser_differentUser_callsService() {
        UUID targetId = UUID.randomUUID();
        doNothing().when(userService).setUserBlockStatus(targetId, true);

        ResponseEntity<ResponseData<Void>> resp =
                controller.blockUser(targetId, true, adminId.toString());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(userService).setUserBlockStatus(targetId, true);
    }

    // ---- deleteUser ----

    @Test
    void deleteUser_selfDelete_throwsApiException() {
        String userId = adminId.toString();

        assertThatThrownBy(() -> controller.deleteUser(adminId, userId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cannot delete yourself");
    }

    @Test
    void deleteUser_differentUser_callsService() {
        UUID targetId = UUID.randomUUID();
        doNothing().when(userService).deleteUser(targetId);

        ResponseEntity<ResponseData<Void>> resp =
                controller.deleteUser(targetId, adminId.toString());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(userService).deleteUser(targetId);
    }

    // ---- blockUserByUsername ----

    @Test
    void blockUserByUsername_delegatesToUserService() {
        doNothing().when(userService).setUserBlockStatusByUsername("target", true, "adminId");

        ResponseEntity<ResponseData<Void>> resp =
                controller.blockUserByUsername("target", true, "adminId");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(userService).setUserBlockStatusByUsername("target", true, "adminId");
    }

    // ---- deleteUserByUsername ----

    @Test
    void deleteUserByUsername_delegatesToUserService() {
        doNothing().when(userService).deleteUserByUsername("target", "adminId");

        ResponseEntity<ResponseData<Void>> resp =
                controller.deleteUserByUsername("target", "adminId");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(userService).deleteUserByUsername("target", "adminId");
    }
}
