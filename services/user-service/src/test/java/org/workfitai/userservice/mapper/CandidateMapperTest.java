package org.workfitai.userservice.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.workfitai.userservice.dto.request.CandidateCreateRequest;
import org.workfitai.userservice.dto.request.CandidateUpdateRequest;
import org.workfitai.userservice.dto.response.CandidateResponse;
import org.workfitai.userservice.enums.EUserRole;
import org.workfitai.userservice.enums.EUserStatus;
import org.workfitai.userservice.model.CandidateEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateMapperTest {

    private CandidateMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new CandidateMapperImpl();
    }

    // ---- toEntity ----

    @Test
    void toEntity_mapsAllFields_exceptPasswordHash() {
        CandidateCreateRequest req = CandidateCreateRequest.builder()
                .fullName("Test Candidate")
                .email("c@test.com")
                .phoneNumber("0901234567")
                .password("secret")
                .careerObjective("Software Engineer")
                .summary("Experienced developer")
                .totalExperience(3)
                .education("Bachelor")
                .skills(List.of("Java", "Spring"))
                .build();

        CandidateEntity entity = mapper.toEntity(req);

        assertThat(entity.getFullName()).isEqualTo("Test Candidate");
        assertThat(entity.getEmail()).isEqualTo("c@test.com");
        assertThat(entity.getPhoneNumber()).isEqualTo("0901234567");
        assertThat(entity.getCareerObjective()).isEqualTo("Software Engineer");
        assertThat(entity.getSummary()).isEqualTo("Experienced developer");
        assertThat(entity.getTotalExperience()).isEqualTo(3);
        assertThat(entity.getEducation()).isEqualTo("Bachelor");
        assertThat(entity.getSkills()).containsExactlyInAnyOrder("Java", "Spring");

        // passwordHash must NOT be mapped from request.password
        assertThat(entity.getPasswordHash()).isNull();

        // userStatus defaults to ACTIVE
        assertThat(entity.getUserStatus()).isEqualTo(EUserStatus.ACTIVE);
    }

    @Test
    void toEntity_passwordNotCopiedToHash() {
        CandidateCreateRequest req = CandidateCreateRequest.builder()
                .fullName("Alice")
                .email("a@test.com")
                .phoneNumber("0901234567")
                .password("mysecret")
                .build();

        CandidateEntity entity = mapper.toEntity(req);

        assertThat(entity.getPasswordHash()).isNull();
    }

    // ---- toResponse ----

    @Test
    void toResponse_mapsAllPublicFields() {
        UUID userId = UUID.randomUUID();
        CandidateEntity entity = CandidateEntity.builder()
                .userId(userId)
                .fullName("Bob")
                .email("b@test.com")
                .username("bob123")
                .phoneNumber("0901234567")
                .userRole(EUserRole.CANDIDATE)
                .userStatus(EUserStatus.ACTIVE)
                .totalExperience(5)
                .education("Master")
                .expectedPosition("Senior Engineer")
                .skills(List.of("Python"))
                .build();

        CandidateResponse response = mapper.toResponse(entity);

        assertThat(response.getFullName()).isEqualTo("Bob");
        assertThat(response.getEmail()).isEqualTo("b@test.com");
        assertThat(response.getUsername()).isEqualTo("bob123");
        assertThat(response.getUserRole()).isEqualTo(EUserRole.CANDIDATE);
        assertThat(response.getUserStatus()).isEqualTo(EUserStatus.ACTIVE);
        assertThat(response.getTotalExperience()).isEqualTo(5);
        assertThat(response.getEducation()).isEqualTo("Master");
        assertThat(response.getSkills()).containsExactly("Python");
    }

    // ---- updateEntityFromUpdateRequest ----

    @Test
    void updateEntityFromUpdateRequest_nullFieldsIgnored() {
        CandidateEntity entity = CandidateEntity.builder()
                .userId(UUID.randomUUID())
                .fullName("Original")
                .email("orig@test.com")
                .totalExperience(2)
                .build();

        CandidateUpdateRequest req = new CandidateUpdateRequest();
        req.setFullName(null);  // should be ignored
        req.setSummary("New summary");

        mapper.updateEntityFromUpdateRequest(req, entity);

        assertThat(entity.getFullName()).isEqualTo("Original");  // unchanged
        assertThat(entity.getSummary()).isEqualTo("New summary");
    }

    @Test
    void updateEntityFromUpdateRequest_nonNullFieldsOverwrite() {
        CandidateEntity entity = CandidateEntity.builder()
                .userId(UUID.randomUUID())
                .fullName("Old Name")
                .totalExperience(1)
                .build();

        CandidateUpdateRequest req = new CandidateUpdateRequest();
        req.setFullName("New Name");
        req.setTotalExperience(5);

        mapper.updateEntityFromUpdateRequest(req, entity);

        assertThat(entity.getFullName()).isEqualTo("New Name");
        assertThat(entity.getTotalExperience()).isEqualTo(5);
    }
}
