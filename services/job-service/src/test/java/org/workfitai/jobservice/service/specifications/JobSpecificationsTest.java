package org.workfitai.jobservice.service.specifications;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.domain.Specification;
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
import org.workfitai.jobservice.security.SecurityUtils;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void jobIdIn_shouldCreateInPredicate() {

        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        Root<Job> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);

        @SuppressWarnings("unchecked")
        Path<UUID> path = mock(Path.class);

        Predicate predicate = mock(Predicate.class);

        when(root.get("jobId")).thenReturn((Path) path);
        when(path.in(List.of(id1, id2))).thenReturn(predicate);

        Specification<Job> specification = JobSpecifications.jobIdIn(List.of(id1, id2));

        Predicate result = specification.toPredicate(root, query, cb);

        assertThat(result).isSameAs(predicate);
        verify(root).get("jobId");
        verify(path).in(List.of(id1, id2));
    }

    @Test
    void keyword_shouldCreateLikeConditions() {

        Root<Job> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);

        @SuppressWarnings("unchecked")
        Path<String> title = mock(Path.class);

        @SuppressWarnings("unchecked")
        Path<String> description = mock(Path.class);

        @SuppressWarnings("unchecked")
        Path<String> shortDescription = mock(Path.class);

        Expression<String> lowerTitle = mock(Expression.class);
        Expression<String> lowerDescription = mock(Expression.class);
        Expression<String> lowerShortDescription = mock(Expression.class);

        Predicate p1 = mock(Predicate.class);
        Predicate p2 = mock(Predicate.class);
        Predicate p3 = mock(Predicate.class);
        Predicate orPredicate = mock(Predicate.class);

        when(root.get("title")).thenReturn((Path) title);
        when(root.get("description")).thenReturn((Path) description);
        when(root.get("shortDescription")).thenReturn((Path) shortDescription);

        when(cb.lower(title)).thenReturn(lowerTitle);
        when(cb.lower(description)).thenReturn(lowerDescription);
        when(cb.lower(shortDescription)).thenReturn(lowerShortDescription);

        when(cb.like(lowerTitle, "%java%")).thenReturn(p1);
        when(cb.like(lowerDescription, "%java%")).thenReturn(p2);
        when(cb.like(lowerShortDescription, "%java%")).thenReturn(p3);

        when(cb.or(p1, p2, p3)).thenReturn(orPredicate);

        Specification<Job> specification = JobSpecifications.keyword("Java");

        Predicate result = specification.toPredicate(root, query, cb);

        assertThat(result).isSameAs(orPredicate);

        verify(cb).lower(title);
        verify(cb).lower(description);
        verify(cb).lower(shortDescription);

        verify(cb).like(lowerTitle, "%java%");
        verify(cb).like(lowerDescription, "%java%");
        verify(cb).like(lowerShortDescription, "%java%");

        verify(cb).or(p1, p2, p3);
    }

    @Test
    void statusPublished_filtersPublishedJobs() {

        List<Job> result = jobRepository.findAll(
                JobSpecifications.statusPublished());

        assertThat(result)
                .extracting(Job::getJobId)
                .contains(
                        publishedJavaHanoi.getJobId(),
                        publishedPythonSaigon.getJobId(),
                        deletedJob.getJobId())
                .doesNotContain(
                        draftJob.getJobId(),
                        closedJob.getJobId());
    }

    @Test
    void statusClosed_filtersClosedJobs() {

        List<Job> result = jobRepository.findAll(
                JobSpecifications.statusClosed());

        assertThat(result)
                .extracting(Job::getJobId)
                .containsExactly(closedJob.getJobId());
    }

    @Test
    void ownedByCurrentUser_buildsSpecification() {

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {

            mocked.when(SecurityUtils::getCurrentUser)
                    .thenReturn("admin");

            Root<Job> root = mock(Root.class);
            CriteriaQuery<?> query = mock(CriteriaQuery.class);
            CriteriaBuilder cb = mock(CriteriaBuilder.class);

            @SuppressWarnings("unchecked")
            Path<String> createdBy = mock(Path.class);

            Predicate predicate = mock(Predicate.class);

            when(root.get("createdBy")).thenReturn((Path) createdBy);
            when(cb.equal(createdBy, "admin")).thenReturn(predicate);

            Predicate result = JobSpecifications
                    .ownedByCurrentUser()
                    .toPredicate(root, query, cb);

            assertThat(result).isSameAs(predicate);

            verify(root).get("createdBy");
            verify(cb).equal(createdBy, "admin");
        }
    }
}
