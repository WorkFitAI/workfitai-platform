package org.workfitai.jobservice.model.mapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workfitai.jobservice.model.Company;
import org.workfitai.jobservice.model.Job;
import org.workfitai.jobservice.model.JobCategory;
import org.workfitai.jobservice.model.Skill;
import org.workfitai.jobservice.model.dto.request.Job.ReqJobDTO;
import org.workfitai.jobservice.model.dto.request.Job.ReqUpdateJobDTO;
import org.workfitai.jobservice.model.dto.response.Job.ResJobDTO;
import org.workfitai.jobservice.model.enums.EmploymentType;
import org.workfitai.jobservice.model.enums.ExperienceLevel;
import org.workfitai.jobservice.model.enums.JobStatus;
import org.workfitai.jobservice.repository.CompanyRepository;
import org.workfitai.jobservice.repository.JobCategoryRepository;
import org.workfitai.jobservice.repository.SkillRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobMapperTest {

    private final JobMapper mapper = JobMapper.INSTANCE;

    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private SkillRepository skillRepository;
    @Mock
    private JobCategoryRepository jobCategoryRepository;

    @Test
    void toEntity_fromCreateDTO_resolvesCompanySkillsAndCategory() {
        Company company = Company.builder().companyNo("C-A").name("FPT").build();
        Skill skill = new Skill("Java");
        UUID skillId = UUID.randomUUID();
        skill.setSkillId(skillId);
        UUID categoryId = UUID.randomUUID();
        JobCategory category = JobCategory.builder().jobCategoryId(categoryId).name("Engineering").build();
        when(companyRepository.findById("C-A")).thenReturn(Optional.of(company));
        when(skillRepository.findAllById(List.of(skillId))).thenReturn(List.of(skill));
        when(jobCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));

        ReqJobDTO dto = ReqJobDTO.builder()
                .title("Java Backend Engineer")
                .companyNo("C-A")
                .skillIds(List.of(skillId))
                .jobCategoryId(categoryId)
                .salaryMin(BigDecimal.valueOf(1000))
                .salaryMax(BigDecimal.valueOf(2000))
                .currency("USD")
                .location("Hanoi")
                .quantity(2)
                .expiresAt(Instant.now().plus(10, ChronoUnit.DAYS))
                .build();

        Job job = mapper.toEntity(dto, companyRepository, skillRepository, jobCategoryRepository);

        assertThat(job.getCompany()).isSameAs(company);
        assertThat(job.getSkills()).containsExactly(skill);
        assertThat(job.getJobCategory()).isSameAs(category);
        assertThat(job.getTitle()).isEqualTo("Java Backend Engineer");
    }

    @Test
    void toEntity_fromCreateDTO_throws_whenCompanyNotFound() {
        when(companyRepository.findById("missing")).thenReturn(Optional.empty());
        ReqJobDTO dto = ReqJobDTO.builder().companyNo("missing").build();

        assertThatThrownBy(() -> mapper.toEntity(dto, companyRepository, skillRepository, jobCategoryRepository))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void toEntity_fromUpdateDTO_ignoresTotalApplications() {
        Company company = Company.builder().companyNo("C-A").name("FPT").build();
        when(companyRepository.findById("C-A")).thenReturn(Optional.of(company));
        ReqUpdateJobDTO dto = ReqUpdateJobDTO.builder().companyNo("C-A").title("Updated Title").build();

        Job job = mapper.toEntity(dto, companyRepository, skillRepository, jobCategoryRepository);

        assertThat(job.getTotalApplications()).isNull();
        assertThat(job.getTitle()).isEqualTo("Updated Title");
    }

    @Test
    void mapCompany_returnsNull_whenCompanyNoIsNull() {
        assertThat(mapper.mapCompany(null, companyRepository)).isNull();
    }

    @Test
    void mapSkills_returnsNull_whenSkillIdsIsNull() {
        assertThat(mapper.mapSkills(null, skillRepository)).isNull();
    }

    @Test
    void mapSkillsToNames_extractsNamesFromSkillList() {
        Skill java = new Skill("Java");
        Skill python = new Skill("Python");

        assertThat(mapper.mapSkillsToNames(List.of(java, python))).containsExactly("Java", "Python");
    }

    @Test
    void mapSkillsToNames_returnsNull_whenSkillsIsNull() {
        assertThat(mapper.mapSkillsToNames(null)).isNull();
    }

    @Test
    void mapJobCategoryName_returnsNull_whenCategoryIsNull() {
        assertThat(mapper.mapJobCategoryName(null)).isNull();
    }

    @Test
    void mapJobCategoryName_returnsName_whenCategoryPresent() {
        JobCategory category = JobCategory.builder().name("Engineering").build();

        assertThat(mapper.mapJobCategoryName(category)).isEqualTo("Engineering");
    }

    @Test
    void map_jobCategoryById_throws_whenNotFound() {
        UUID categoryId = UUID.randomUUID();
        when(jobCategoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mapper.map(categoryId, jobCategoryRepository))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Job category not found");
    }

    @Test
    void map_jobCategoryById_returnsNull_whenIdIsNull() {
        assertThat(mapper.map(null, jobCategoryRepository)).isNull();
    }

    @Test
    void mapStatus_usesEffectiveStatus_autoClosingExpiredJobs() {
        Job job = new Job();
        job.setStatus(JobStatus.PUBLISHED);
        job.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));

        assertThat(mapper.mapStatus(job)).isEqualTo(JobStatus.CLOSED);
    }

    @Test
    void toResJobDTO_mapsSkillNamesAndPostIdAndEffectiveStatus() {
        Job job = new Job();
        job.setJobId(UUID.randomUUID());
        job.setTitle("Java Dev");
        job.setStatus(JobStatus.PUBLISHED);
        job.setExpiresAt(Instant.now().plus(5, ChronoUnit.DAYS));
        job.setSkills(List.of(new Skill("Java")));
        job.setCompany(Company.builder().companyNo("C-A").name("FPT").build());
        job.setEmploymentType(EmploymentType.FULL_TIME);
        job.setExperienceLevel(ExperienceLevel.SENIOR);

        ResJobDTO dto = mapper.toResJobDTO(job);

        assertThat(dto.getPostId()).isEqualTo(job.getJobId());
        assertThat(dto.getSkillNames()).containsExactly("Java");
        assertThat(dto.getStatus()).isEqualTo(JobStatus.PUBLISHED);
    }

    @Test
    void toResJobDTO_mapsCurrency() {
        Job job = new Job();
        job.setJobId(UUID.randomUUID());
        job.setStatus(JobStatus.PUBLISHED);
        job.setExpiresAt(Instant.now().plus(5, ChronoUnit.DAYS));
        job.setCurrency("USD");

        ResJobDTO dto = mapper.toResJobDTO(job);

        assertThat(dto.getCurrency()).isEqualTo("USD");
    }
}
