package org.workfitai.userservice.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.workfitai.userservice.dto.request.HRCreateRequest;
import org.workfitai.userservice.dto.request.HRUpdateRequest;
import org.workfitai.userservice.dto.response.HRResponse;
import org.workfitai.userservice.enums.EUserRole;
import org.workfitai.userservice.enums.EUserStatus;
import org.workfitai.userservice.model.HREntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HRMapperTest {

    private HRMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new HRMapperImpl();
    }

    // ---- toEntity ----

    @Test
    void toEntity_mapsAllHrFields() {
        HRCreateRequest req = HRCreateRequest.builder()
                .fullName("HR User")
                .email("hr@company.com")
                .phoneNumber("0901234567")
                .password("secret")
                .department("Engineering")
                .companyId(UUID.randomUUID().toString())
                .address("123 Main St")
                .build();

        HREntity entity = mapper.toEntity(req);

        assertThat(entity.getFullName()).isEqualTo("HR User");
        assertThat(entity.getEmail()).isEqualTo("hr@company.com");
        assertThat(entity.getPhoneNumber()).isEqualTo("0901234567");
        assertThat(entity.getDepartment()).isEqualTo("Engineering");
        assertThat(entity.getAddress()).isEqualTo("123 Main St");
    }

    // ---- toResponse ----

    @Test
    void toResponse_mapsCompanyNoToCompanyName() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        HREntity entity = HREntity.builder()
                .userId(userId)
                .fullName("HR User")
                .email("hr@company.com")
                .username("hr_user")
                .phoneNumber("0901234567")
                .userRole(EUserRole.HR)
                .userStatus(EUserStatus.ACTIVE)
                .department("Engineering")
                .companyId(companyId)
                .companyNo("TAX001")
                .address("123 Main St")
                .build();

        HRResponse response = mapper.toResponse(entity);

        assertThat(response.getFullName()).isEqualTo("HR User");
        assertThat(response.getEmail()).isEqualTo("hr@company.com");
        assertThat(response.getUserRole()).isEqualTo(EUserRole.HR);
        assertThat(response.getDepartment()).isEqualTo("Engineering");
        // companyNo → companyName (mapper annotation: source="companyNo", target="companyName")
        assertThat(response.getCompanyName()).isEqualTo("TAX001");
    }

    @Test
    void toResponse_hrManagerRole_preserved() {
        HREntity entity = HREntity.builder()
                .userId(UUID.randomUUID())
                .fullName("HR Manager")
                .email("mgr@company.com")
                .username("hr_mgr")
                .userRole(EUserRole.HR_MANAGER)
                .userStatus(EUserStatus.WAIT_APPROVED)
                .companyId(UUID.randomUUID())
                .companyNo("TAX002")
                .build();

        HRResponse response = mapper.toResponse(entity);

        assertThat(response.getUserRole()).isEqualTo(EUserRole.HR_MANAGER);
        assertThat(response.getUserStatus()).isEqualTo(EUserStatus.WAIT_APPROVED);
    }

    // ---- updateEntityFromUpdateRequest ----

    @Test
    void updateEntityFromUpdateRequest_nullFieldsIgnored() {
        HREntity entity = HREntity.builder()
                .userId(UUID.randomUUID())
                .fullName("Original HR")
                .department("IT")
                .companyId(UUID.randomUUID())
                .companyNo("TAX001")
                .build();

        HRUpdateRequest req = new HRUpdateRequest();
        req.setFullName(null);
        req.setDepartment("Sales");

        mapper.updateEntityFromUpdateRequest(req, entity);

        assertThat(entity.getFullName()).isEqualTo("Original HR");
        assertThat(entity.getDepartment()).isEqualTo("Sales");
    }

    @Test
    void updateEntityFromUpdateRequest_nonNullFieldsOverwrite() {
        HREntity entity = HREntity.builder()
                .userId(UUID.randomUUID())
                .fullName("Old Name")
                .address("Old Address")
                .companyId(UUID.randomUUID())
                .companyNo("TAX001")
                .build();

        HRUpdateRequest req = new HRUpdateRequest();
        req.setFullName("New Name");
        req.setAddress("New Address");

        mapper.updateEntityFromUpdateRequest(req, entity);

        assertThat(entity.getFullName()).isEqualTo("New Name");
        assertThat(entity.getAddress()).isEqualTo("New Address");
    }
}
