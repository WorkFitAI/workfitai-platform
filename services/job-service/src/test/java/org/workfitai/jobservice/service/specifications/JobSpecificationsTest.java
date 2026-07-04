package org.workfitai.jobservice.service.specifications;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.workfitai.jobservice.model.Company;
import org.workfitai.jobservice.model.Job;
import org.workfitai.jobservice.model.Skill;
import org.workfitai.jobservice.model.enums.EmploymentType;
import org.workfitai.jobservice.model.enums.ExperienceLevel;
import org.workfitai.jobservice.model.enums.JobStatus;
import org.workfitai.jobservice.repository.CompanyRepository;
import org.workfitai.jobservice.repository.JobRepository;
import org.workfitai.jobservice.repository.SkillRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class JobSpecificationsTest {

    @Autowired
    private JobRepository jobRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private SkillRepository skillRepository;

    private Company companyA;
    private Company companyB;
    private Skill javaSkill;
    private Skill pythonSkill;

    private Job publishedJavaHanoi;
    private Job publishedPythonSaigon;
    private Job draftJob;
    private Job deletedJob;
    private Job closedJob;

    @BeforeEach
    void seed() {
        companyA = companyRepository.save(Company.builder().companyNo("C-A").name("FPT").build());
        companyB = companyRepository.save(Company.builder().companyNo("C-B").name("KMS").build());

        javaSkill = skillRepository.save(new Skill("Java"));
        pythonSkill = skillRepository.save(new Skill("Python"));

        publishedJavaHanoi = jobRepository.save(job("Java Backend Engineer", companyA, JobStatus.PUBLISHED,
                "Hanoi", ExperienceLevel.SENIOR, List.of(javaSkill), false));

        publishedPythonSaigon = jobRepository.save(job("Python Data Engineer", companyB, JobStatus.PUBLISHED,
                "Ho Chi Minh", ExperienceLevel.JUNIOR, List.of(pythonSkill), false));

        draftJob = jobRepository.save(job("Draft Frontend Role", companyA, JobStatus.DRAFT,
                "Hanoi", ExperienceLevel.MID, List.of(javaSkill), false));

        deletedJob = jobRepository.save(job("Deleted Backend Role", companyA, JobStatus.PUBLISHED,
                "Hanoi", ExperienceLevel.SENIOR, List.of(javaSkill), true));

        closedJob = jobRepository.save(job("Closed Java Role", companyA, JobStatus.CLOSED,
                "Hanoi", ExperienceLevel.SENIOR, List.of(javaSkill), false));
    }

    private Job job(String title, Company company, JobStatus status, String location,
            ExperienceLevel experience, List<Skill> skills, boolean deleted) {
        return Job.builder()
                .title(title)
                .description("A".repeat(25))
                .shortDescription("Short description text")
                .employmentType(EmploymentType.FULL_TIME)
                .experienceLevel(experience)
                .salaryMin(BigDecimal.valueOf(1000))
                .salaryMax(BigDecimal.valueOf(2000))
                .currency("USD")
                .location(location)
                .quantity(1)
                .totalApplications(0)
                .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
                .status(status)
                .educationLevel("Bachelor degree")
                .views(0L)
                .company(company)
                .isDeleted(deleted)
                .skills(skills)
                .build();
    }

    @Test
    void statusIn_filtersByGivenStatuses() {
        List<Job> result = jobRepository.findAll(JobSpecifications.statusIn(List.of(JobStatus.PUBLISHED)));

        assertThat(result).extracting(Job::getJobId)
                .contains(publishedJavaHanoi.getJobId(), publishedPythonSaigon.getJobId(), deletedJob.getJobId())
                .doesNotContain(draftJob.getJobId(), closedJob.getJobId());
    }

    @Test
    void isNoDeleted_excludesDeletedJobs() {
        List<Job> result = jobRepository.findAll(JobSpecifications.isNoDeleted());

        assertThat(result).extracting(Job::getJobId).doesNotContain(deletedJob.getJobId());
    }

    @Test
    void isNotStatusDraft_excludesDraftJobs() {
        List<Job> result = jobRepository.findAll(JobSpecifications.isNotStatusDraft());

        assertThat(result).extracting(Job::getJobId).doesNotContain(draftJob.getJobId());
    }

    @Test
    void hasCompanyId_filtersByCompanyNo() {
        List<Job> result = jobRepository.findAll(JobSpecifications.hasCompanyId("C-B"));

        assertThat(result).extracting(Job::getJobId).containsExactly(publishedPythonSaigon.getJobId());
    }

    @Test
    void notJobId_excludesGivenJob() {
        List<Job> result = jobRepository.findAll(JobSpecifications.notJobId(publishedJavaHanoi.getJobId()));

        assertThat(result).extracting(Job::getJobId).doesNotContain(publishedJavaHanoi.getJobId());
    }

    @Test
    void hasSkills_matchesJobsWithAnyOfGivenSkills() {
        List<Job> result = jobRepository.findAll(JobSpecifications.hasSkills(List.of(pythonSkill.getSkillId())));

        assertThat(result).extracting(Job::getJobId).containsExactly(publishedPythonSaigon.getJobId());
    }

    @Test
    void hasLocation_isCaseInsensitive() {
        List<Job> result = jobRepository.findAll(JobSpecifications.hasLocation("HANOI"));

        assertThat(result).extracting(Job::getJobId)
                .contains(publishedJavaHanoi.getJobId(), draftJob.getJobId());
    }

    @Test
    void hasTitleLike_matchesSubstringCaseInsensitive() {
        List<Job> result = jobRepository.findAll(JobSpecifications.hasTitleLike("java"));

        assertThat(result).extracting(Job::getJobId)
                .contains(publishedJavaHanoi.getJobId(), closedJob.getJobId());
    }

    @Test
    void hasExperience_filtersByExperienceLevel() {
        List<Job> result = jobRepository.findAll(JobSpecifications.hasExperience(ExperienceLevel.JUNIOR));

        assertThat(result).extracting(Job::getJobId).containsExactly(publishedPythonSaigon.getJobId());
    }

    @Test
    void featuredJobs_excludesDeletedAndDraft() {
        List<Job> result = jobRepository.findAll(JobSpecifications.featuredJobs());

        assertThat(result).extracting(Job::getJobId)
                .contains(publishedJavaHanoi.getJobId(), publishedPythonSaigon.getJobId(), closedJob.getJobId())
                .doesNotContain(draftJob.getJobId(), deletedJob.getJobId());
    }

    @Test
    void isActive_onlyReturnsJobsNotYetExpired() {
        List<Job> result = jobRepository.findAll(JobSpecifications.isActive());

        assertThat(result).extracting(Job::getJobId).contains(publishedJavaHanoi.getJobId());
    }

    @Test
    void similarJobs_combinesSkillAndLocationAndExcludesSelfAndDraftAndDeleted() {
        List<Job> result = jobRepository.findAll(JobSpecifications.similarJobs(
                closedJob.getJobId(),
                List.of(javaSkill.getSkillId()),
                "Hanoi",
                "Java",
                ExperienceLevel.SENIOR));

        assertThat(result).extracting(Job::getJobId)
                .contains(publishedJavaHanoi.getJobId())
                .doesNotContain(closedJob.getJobId(), draftJob.getJobId(), deletedJob.getJobId());
    }

    @Test
    void similarJobs_withEmptyFilters_stillExcludesSelfDraftAndDeleted() {
        List<Job> result = jobRepository.findAll(JobSpecifications.similarJobs(
                publishedJavaHanoi.getJobId(),
                List.of(),
                "Nowhere",
                "zzz-no-match",
                ExperienceLevel.LEAD));

        assertThat(result).extracting(Job::getJobId)
                .doesNotContain(publishedJavaHanoi.getJobId(), draftJob.getJobId(), deletedJob.getJobId());
    }
}
