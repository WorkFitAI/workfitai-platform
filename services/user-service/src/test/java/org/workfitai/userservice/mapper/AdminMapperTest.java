package org.workfitai.userservice.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.workfitai.userservice.dto.request.AdminCreateRequest;
import org.workfitai.userservice.dto.request.AdminUpdateRequest;
import org.workfitai.userservice.dto.response.AdminResponse;
import org.workfitai.userservice.enums.EUserRole;
import org.workfitai.userservice.enums.EUserStatus;
import org.workfitai.userservice.model.AdminEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AdminMapperTest {

    private AdminMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new AdminMapperImpl();
    }

    // ---- toEntity ----

    @Test
    void toEntity_mapsAllAdminFields() {
        AdminCreateRequest req = new AdminCreateRequest();
        req.setFullName("Super Admin");
        req.setEmail("admin@workfitai.com");
        req.setPhoneNumber("0901234567");
        req.setPassword("secret");

        AdminEntity entity = mapper.toEntity(req);

        assertThat(entity.getFullName()).isEqualTo("Super Admin");
        assertThat(entity.getEmail()).isEqualTo("admin@workfitai.com");
        assertThat(entity.getPhoneNumber()).isEqualTo("0901234567");
    }

    // ---- toResponse ----

    @Test
    void toResponse_mapsAllFields() {
        UUID userId = UUID.randomUUID();
        AdminEntity entity = AdminEntity.builder()
                .userId(userId)
                .fullName("Super Admin")
                .email("admin@workfitai.com")
                .username("superadmin")
                .phoneNumber("0901234567")
                .userRole(EUserRole.ADMIN)
                .userStatus(EUserStatus.ACTIVE)
                .build();

        AdminResponse response = mapper.toResponse(entity);

        assertThat(response.getFullName()).isEqualTo("Super Admin");
        assertThat(response.getEmail()).isEqualTo("admin@workfitai.com");
        assertThat(response.getUsername()).isEqualTo("superadmin");
        assertThat(response.getUserRole()).isEqualTo(EUserRole.ADMIN);
        assertThat(response.getUserStatus()).isEqualTo(EUserStatus.ACTIVE);
    }

    // ---- updateEntityFromUpdateRequest ----

    @Test
    void updateEntityFromUpdateRequest_nullFieldsIgnored() {
        AdminEntity entity = AdminEntity.builder()
                .userId(UUID.randomUUID())
                .fullName("Original Admin")
                .email("orig@test.com")
                .build();

        AdminUpdateRequest req = new AdminUpdateRequest();
        req.setFullName(null);
        req.setPhoneNumber("0909999999");

        mapper.updateEntityFromUpdateRequest(req, entity);

        assertThat(entity.getFullName()).isEqualTo("Original Admin");
        assertThat(entity.getPhoneNumber()).isEqualTo("0909999999");
    }

    @Test
    void updateEntityFromUpdateRequest_nonNullFieldsOverwrite() {
        AdminEntity entity = AdminEntity.builder()
                .userId(UUID.randomUUID())
                .fullName("Old Admin")
                .build();

        AdminUpdateRequest req = new AdminUpdateRequest();
        req.setFullName("New Admin");

        mapper.updateEntityFromUpdateRequest(req, entity);

        assertThat(entity.getFullName()).isEqualTo("New Admin");
    }
}
