package org.workfitai.userservice.service.impl;

import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.workfitai.userservice.dto.request.AdminCreateRequest;
import org.workfitai.userservice.dto.request.AdminUpdateRequest;
import org.workfitai.userservice.dto.response.AdminResponse;
import org.workfitai.userservice.enums.EUserRole;
import org.workfitai.userservice.enums.EUserStatus;
import org.workfitai.userservice.exception.ApiException;
import org.workfitai.userservice.mapper.AdminMapper;
import org.workfitai.userservice.model.AdminEntity;
import org.workfitai.userservice.repository.AdminRepository;
import org.workfitai.userservice.specification.AdminSpecification;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminServiceImplTest {

    @Mock AdminRepository adminRepository;
    @Mock AdminMapper adminMapper;
    @Mock AdminSpecification adminSpecification;
    @Mock Validator validator;

    @InjectMocks
    AdminServiceImpl adminService;

    private UUID adminId;
    private AdminEntity adminEntity;
    private AdminResponse adminResponse;

    @BeforeEach
    void setUp() {
        adminId = UUID.randomUUID();

        adminEntity = AdminEntity.builder()
                .userId(adminId)
                .email("admin@test.com")
                .username("admin_user")
                .fullName("Test Admin")
                .userRole(EUserRole.ADMIN)
                .userStatus(EUserStatus.ACTIVE)
                .build();

        adminResponse = mock(AdminResponse.class);
        when(validator.validate(any())).thenReturn(Set.of());
    }

    // ---- create ----

    @Test
    void create_success_savesAndReturns() {
        AdminCreateRequest req = buildCreateRequest("new@test.com");
        when(adminRepository.existsByEmail(req.getEmail())).thenReturn(false);
        when(adminMapper.toEntity(req)).thenReturn(adminEntity);
        when(adminRepository.save(adminEntity)).thenReturn(adminEntity);
        when(adminMapper.toResponse(adminEntity)).thenReturn(adminResponse);

        AdminResponse result = adminService.create(req);

        assertThat(result).isSameAs(adminResponse);
        verify(adminRepository).save(adminEntity);
        assertThat(adminEntity.getUserRole()).isEqualTo(EUserRole.ADMIN);
    }

    @Test
    void create_emailDuplicate_throws() {
        AdminCreateRequest req = buildCreateRequest("dup@test.com");
        when(adminRepository.existsByEmail(req.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> adminService.create(req))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Email already exists");
    }

    // ---- update ----

    @Test
    void update_success_updatesAndReturns() {
        AdminUpdateRequest req = new AdminUpdateRequest();
        when(adminRepository.findById(adminId)).thenReturn(Optional.of(adminEntity));
        when(adminRepository.save(adminEntity)).thenReturn(adminEntity);
        when(adminMapper.toResponse(adminEntity)).thenReturn(adminResponse);

        AdminResponse result = adminService.update(adminId, req);

        assertThat(result).isSameAs(adminResponse);
        verify(adminMapper).updateEntityFromUpdateRequest(req, adminEntity);
    }

    @Test
    void update_notFound_throws() {
        when(adminRepository.findById(adminId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.update(adminId, new AdminUpdateRequest()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Admin not found");
    }

    // ---- delete ----

    @Test
    void delete_success_removesById() {
        when(adminRepository.existsById(adminId)).thenReturn(true);

        adminService.delete(adminId);

        verify(adminRepository).deleteById(adminId);
    }

    @Test
    void delete_notFound_throws() {
        when(adminRepository.existsById(adminId)).thenReturn(false);

        assertThatThrownBy(() -> adminService.delete(adminId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Admin not found");
    }

    // ---- getById ----

    @Test
    void getById_found_returnsResponse() {
        when(adminRepository.findById(adminId)).thenReturn(Optional.of(adminEntity));
        when(adminMapper.toResponse(adminEntity)).thenReturn(adminResponse);

        assertThat(adminService.getById(adminId)).isSameAs(adminResponse);
    }

    @Test
    void getById_notFound_throws() {
        when(adminRepository.findById(adminId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.getById(adminId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Admin not found");
    }

    // ---- search ----

    @Test
    void search_delegatesToRepo() {
        Specification<AdminEntity> spec = mock(Specification.class);
        when(adminSpecification.filter("kw")).thenReturn(spec);
        when(adminRepository.findAll(eq(spec), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(adminEntity)));
        when(adminMapper.toResponse(adminEntity)).thenReturn(adminResponse);

        Page<AdminResponse> result = adminService.search("kw", PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    // ---- helpers ----

    private AdminCreateRequest buildCreateRequest(String email) {
        AdminCreateRequest req = new AdminCreateRequest();
        req.setEmail(email);
        req.setPhoneNumber("0900000001");
        req.setPassword("pass123");
        req.setFullName("Test Admin");
        return req;
    }
}
