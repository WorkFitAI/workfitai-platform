package org.workfitai.jobservice.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.workfitai.jobservice.client.UserFeignClient;
import org.workfitai.jobservice.config.errors.InvalidDataException;
import org.workfitai.jobservice.config.errors.NoPermissionException;
import org.workfitai.jobservice.config.errors.ResourceConflictException;
import org.workfitai.jobservice.messaging.NotificationProducer;
import org.workfitai.jobservice.model.Company;
import org.workfitai.jobservice.model.Job;
import org.workfitai.jobservice.model.JobCategory;
import org.workfitai.jobservice.model.Skill;
import org.workfitai.jobservice.model.dto.HrNotificationSettingsDTO;
import org.workfitai.jobservice.model.dto.kafka.UserInfoServeForJobResponse;
import org.workfitai.jobservice.model.dto.request.Job.ReqJobDTO;
import org.workfitai.jobservice.model.dto.request.Job.ReqUpdateJobDTO;
import org.workfitai.jobservice.model.dto.response.Job.ResCreateJobDTO;
import org.workfitai.jobservice.model.dto.response.Job.ResModifyStatus;
import org.workfitai.jobservice.model.dto.response.Job.ResUpdateJobDTO;
import org.workfitai.jobservice.model.dto.response.ResultPaginationDTO;
import org.workfitai.jobservice.model.enums.ExperienceLevel;
import org.workfitai.jobservice.model.enums.JobStatus;
import org.workfitai.jobservice.model.mapper.JobMapper;
import org.workfitai.jobservice.repository.CompanyRepository;
import org.workfitai.jobservice.repository.JobCategoryRepository;
import org.workfitai.jobservice.repository.JobRepository;
import org.workfitai.jobservice.repository.SkillRepository;
import org.workfitai.jobservice.service.impl.JobService;
import org.workfitai.jobservice.service.impl.NotificationService;
import org.workfitai.jobservice.service.impl.OutboxService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Spy
    JobMapper mapper = JobMapper.INSTANCE;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private SkillRepository skillRepository;
    @Mock
    private JobRepository jobRepository;
    @Mock
    private JobCategoryRepository jobCategoryRepository;
    @Mock
    private JobEventProducer jobEventProducer;
    @Mock
    private NotificationProducer notificationProducer;
    @Mock
    private UserFeignClient userFeignClient;
    @Mock
    private NotificationService notificationService;
    @Mock
    private OutboxService outboxService;
    @InjectMocks
    private JobService jobService;

    private void authenticateAsCompany(String companyNo) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("companyId", companyNo)
                .subject("hr1")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private Company company(String companyNo) {
        return Company.builder().companyNo(companyNo).name("FPT Software").build();
    }

    private Job existingJob(Company company, JobStatus status, int totalApplications) {
        Job job = new Job();
        job.setJobId(UUID.randomUUID());
        job.setTitle("Java Developer");
        job.setCompany(company);
        job.setStatus(status);
        job.setTotalApplications(totalApplications);
        job.setQuantity(5);
        job.setDeleted(false);
        job.setSkills(List.of());
        return job;
    }

    // ---------------- fetchAll / fetchAllForHr / fetchAllForAdmin / fetchJobsByCompany ----------------

    @Test
    void fetchAll_returnsPaginatedResult() {
        Page<Job> page = new PageImpl<>(List.of(existingJob(company("FPT#25"), JobStatus.PUBLISHED, 0)));
        when(jobRepository.findAll(
                Mockito.<org.springframework.data.jpa.domain.Specification<Job>>any(),
                Mockito.<Pageable>any())).thenReturn(page);

        ResultPaginationDTO result = jobService.fetchAll(null, PageRequest.of(0, 10));

        assertThat(result).isNotNull();
        assertThat((List<?>) result.getResult()).hasSize(1);
    }

    @Test
    void fetchAllForHr_scopesToCompany() {
        authenticateAsCompany("FPT#25");
        Page<Job> page = new PageImpl<>(List.of(existingJob(company("FPT#25"), JobStatus.PUBLISHED, 0)));
        when(jobRepository.findAll(
                Mockito.<org.springframework.data.jpa.domain.Specification<Job>>any(),
                Mockito.<Pageable>any())).thenReturn(page);

        ResultPaginationDTO result = jobService.fetchAllForHr(null, PageRequest.of(0, 10));

        assertThat(result).isNotNull();
        assertThat((List<?>) result.getResult()).hasSize(1);
    }

    @Test
    void fetchAllForAdmin_returnsPaginatedResult() {
        Page<Job> page = new PageImpl<>(List.of(existingJob(company("FPT#25"), JobStatus.PUBLISHED, 0)));
        when(jobRepository.findAll(
                Mockito.<org.springframework.data.jpa.domain.Specification<Job>>any(),
                Mockito.<Pageable>any())).thenReturn(page);

        ResultPaginationDTO result = jobService.fetchAllForAdmin(null, PageRequest.of(0, 10));

        assertThat(result).isNotNull();
        assertThat((List<?>) result.getResult()).hasSize(1);
    }

    @Test
    void fetchJobsByCompany_returnsPaginatedResult() {
        Page<Job> page = new PageImpl<>(List.of(existingJob(company("FPT#25"), JobStatus.PUBLISHED, 0)));
        when(jobRepository.findAll(
                Mockito.<org.springframework.data.jpa.domain.Specification<Job>>any(),
                Mockito.<Pageable>any())).thenReturn(page);

        ResultPaginationDTO result = jobService.fetchJobsByCompany("FPT#25", PageRequest.of(0, 10));

        assertThat(result).isNotNull();
        assertThat((List<?>) result.getResult()).hasSize(1);
    }

    // ---------------- fetchJobById ----------------

    @Test
    void fetchJobById_returnsNull_whenNotFound() {
        UUID jobId = UUID.randomUUID();
        when(jobRepository.findById(jobId)).thenReturn(Optional.empty());

        assertThat(jobService.fetchJobById(jobId)).isNull();
    }

    @Test
    void fetchJobById_returnsNull_whenDeleted() {
        Job job = existingJob(company("FPT#25"), JobStatus.PUBLISHED, 0);
        job.setDeleted(true);
        when(jobRepository.findById(job.getJobId())).thenReturn(Optional.of(job));

        assertThat(jobService.fetchJobById(job.getJobId())).isNull();
    }

    @Test
    void fetchJobById_returnsNull_whenDraft() {
        Job job = existingJob(company("FPT#25"), JobStatus.DRAFT, 0);
        when(jobRepository.findById(job.getJobId())).thenReturn(Optional.of(job));

        assertThat(jobService.fetchJobById(job.getJobId())).isNull();
    }

    @Test
    void fetchJobById_returnsDetails_whenPublished() {
        Job job = existingJob(company("FPT#25"), JobStatus.PUBLISHED, 0);
        when(jobRepository.findById(job.getJobId())).thenReturn(Optional.of(job));

        var result = jobService.fetchJobById(job.getJobId());

        assertThat(result).isNotNull();
    }

    // ---------------- fetchJobByIdForHr ----------------

    @Test
    void fetchJobByIdForHr_throwsNoPermission_whenCompanyMissing() {
        authenticateAsCompany("FPT#25");
        UUID jobId = UUID.randomUUID();
        when(companyRepository.findById("FPT#25")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.fetchJobByIdForHr(jobId))
                .isInstanceOf(NoPermissionException.class);
    }

    @Test
    void fetchJobByIdForHr_throwsResourceNotFound_whenJobMissing() {
        authenticateAsCompany("FPT#25");
        UUID jobId = UUID.randomUUID();
        when(companyRepository.findById("FPT#25")).thenReturn(Optional.of(company("FPT#25")));
        when(jobRepository.findByIdAndCreatedBy(jobId, "hr1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.fetchJobByIdForHr(jobId))
                .isInstanceOf(org.springframework.data.rest.webmvc.ResourceNotFoundException.class);
    }

    @Test
    void fetchJobByIdForHr_returnsNull_whenDeleted() {
        authenticateAsCompany("FPT#25");
        Job job = existingJob(company("FPT#25"), JobStatus.PUBLISHED, 0);
        job.setDeleted(true);
        when(companyRepository.findById("FPT#25")).thenReturn(Optional.of(company("FPT#25")));
        when(jobRepository.findByIdAndCreatedBy(job.getJobId(), "hr1")).thenReturn(Optional.of(job));

        assertThat(jobService.fetchJobByIdForHr(job.getJobId())).isNull();
    }

    @Test
    void fetchJobByIdForHr_returnsDetails_whenFound() {
        authenticateAsCompany("FPT#25");
        Job job = existingJob(company("FPT#25"), JobStatus.PUBLISHED, 0);
        when(companyRepository.findById("FPT#25")).thenReturn(Optional.of(company("FPT#25")));
        when(jobRepository.findByIdAndCreatedBy(job.getJobId(), "hr1")).thenReturn(Optional.of(job));

        var result = jobService.fetchJobByIdForHr(job.getJobId());

        assertThat(result).isNotNull();
    }

    // ---------------- fetchJobByIdForAdmin / getJobById ----------------

    @Test
    void fetchJobByIdForAdmin_returnsDetails() {
        Job job = existingJob(company("FPT#25"), JobStatus.PUBLISHED, 0);
        when(jobRepository.findById(job.getJobId())).thenReturn(Optional.of(job));

        var result = jobService.fetchJobByIdForAdmin(job.getJobId());

        assertThat(result).isNotNull();
    }

    @Test
    void getJobById_delegatesToRepository() {
        Job job = existingJob(company("FPT#25"), JobStatus.PUBLISHED, 0);
        when(jobRepository.findById(job.getJobId())).thenReturn(Optional.of(job));

        assertThat(jobService.getJobById(job.getJobId())).contains(job);
    }

    // ---------------- createJob ----------------

    @Test
    void createJob_success() {
        authenticateAsCompany("FPT#25");
        UUID skillId1 = UUID.randomUUID();
        UUID skillId2 = UUID.randomUUID();

        ReqJobDTO reqJobDTO = new ReqJobDTO();
        reqJobDTO.setTitle("Java Developer");
        reqJobDTO.setCompanyNo("FPT#25");
        reqJobDTO.setSkillIds(List.of(skillId1, skillId2));

        Company company = company("FPT#25");
        Skill skill1 = Skill.builder().skillId(skillId1).name("Java").build();
        Skill skill2 = Skill.builder().skillId(skillId2).name("Spring Boot").build();

        when(companyRepository.findById("FPT#25")).thenReturn(Optional.of(company));
        when(skillRepository.findAllById(List.of(skillId1, skillId2))).thenReturn(List.of(skill1, skill2));
        when(skillRepository.findByIdIn(List.of(skillId1, skillId2))).thenReturn(List.of(skill1, skill2));

        Job savedJob = new Job();
        savedJob.setJobId(UUID.randomUUID());
        savedJob.setTitle("Java Developer");
        savedJob.setCompany(company);
        savedJob.setSkills(List.of(skill1, skill2));
        savedJob.setStatus(JobStatus.DRAFT);

        when(jobRepository.save(Mockito.<Job>any())).thenReturn(savedJob);

        ResCreateJobDTO result = jobService.createJob(reqJobDTO);

        assertThat(result).isNotNull();
        assertThat(result.getPostId()).isEqualTo(savedJob.getJobId());
        verify(jobRepository).save(any(Job.class));
        verify(jobEventProducer).publishJobCreated(savedJob);
    }

    @Test
    void createJob_throwsNoPermission_whenNotAuthenticated() {
        ReqJobDTO reqJobDTO = new ReqJobDTO();
        reqJobDTO.setCompanyNo("FPT#25");

        assertThatThrownBy(() -> jobService.createJob(reqJobDTO))
                .isInstanceOf(NoPermissionException.class);

        verify(jobRepository, never()).save(any());
    }

    @Test
    void createJob_throwsNoPermission_whenCompanyNoMismatch() {
        authenticateAsCompany("FPT#25");
        ReqJobDTO reqJobDTO = new ReqJobDTO();
        reqJobDTO.setCompanyNo("OTHER#1");

        assertThatThrownBy(() -> jobService.createJob(reqJobDTO))
                .isInstanceOf(NoPermissionException.class);

        verify(jobRepository, never()).save(any());
    }

    // ---------------- updateJob ----------------

    @Test
    void updateJob_throwsConflict_whenCompanyNoMismatch() {
        authenticateAsCompany("OTHER#1");
        ReqUpdateJobDTO dto = ReqUpdateJobDTO.builder().companyNo("FPT#25").build();
        Job dbJob = existingJob(company("FPT#25"), JobStatus.DRAFT, 0);

        assertThatThrownBy(() -> jobService.updateJob(dto, dbJob))
                .isInstanceOf(NoPermissionException.class);
    }

    @Test
    void updateJob_throwsNoPermission_whenCompanyNotFound() {
        authenticateAsCompany("FPT#25");
        ReqUpdateJobDTO dto = ReqUpdateJobDTO.builder().companyNo("FPT#25").build();
        Job dbJob = existingJob(company("FPT#25"), JobStatus.DRAFT, 0);
        when(companyRepository.findById("FPT#25")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.updateJob(dto, dbJob))
                .isInstanceOf(NoPermissionException.class);
    }

    @Test
    void updateJob_throwsConflict_whenJobDeleted() {
        authenticateAsCompany("FPT#25");
        ReqUpdateJobDTO dto = ReqUpdateJobDTO.builder().companyNo("FPT#25").build();
        Job dbJob = existingJob(company("FPT#25"), JobStatus.DRAFT, 0);
        dbJob.setDeleted(true);
        when(companyRepository.findById("FPT#25")).thenReturn(Optional.of(company("FPT#25")));

        assertThatThrownBy(() -> jobService.updateJob(dto, dbJob))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessage(org.workfitai.jobservice.util.MessageConstant.JOB_NOT_FOUND);
    }

    @Test
    void updateJob_throwsConflict_whenPublishedWithApplications() {
        authenticateAsCompany("FPT#25");
        ReqUpdateJobDTO dto = ReqUpdateJobDTO.builder().companyNo("FPT#25").build();
        Job dbJob = existingJob(company("FPT#25"), JobStatus.PUBLISHED, 3);
        when(companyRepository.findById("FPT#25")).thenReturn(Optional.of(company("FPT#25")));

        assertThatThrownBy(() -> jobService.updateJob(dto, dbJob))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessage(org.workfitai.jobservice.util.MessageConstant.JOB_UPDATE_CONFLICT);
    }

    @Test
    void updateJob_success_publishesChangesEvent() {
        authenticateAsCompany("FPT#25");
        Job dbJob = existingJob(company("FPT#25"), JobStatus.DRAFT, 0);
        dbJob.setTitle("Old Title");

        ReqUpdateJobDTO dto = ReqUpdateJobDTO.builder()
                .companyNo("FPT#25")
                .title("New Title")
                .jobCategoryId(UUID.randomUUID())
                .build();

        when(companyRepository.findById("FPT#25")).thenReturn(Optional.of(company("FPT#25")));
        when(jobCategoryRepository.findById(dto.getJobCategoryId()))
                .thenReturn(Optional.of(JobCategory.builder().jobCategoryId(dto.getJobCategoryId()).name("Backend").build()));
        when(jobRepository.save(dbJob)).thenReturn(dbJob);

        ResUpdateJobDTO result = jobService.updateJob(dto, dbJob);

        assertThat(result).isNotNull();
        assertThat(dbJob.getTitle()).isEqualTo("New Title");
        verify(jobEventProducer).publishJobUpdated(eq(dbJob), any());
    }

    @Test
    void updateJob_doesNotPublishEvent_whenNoChangesDetected() {
        authenticateAsCompany("FPT#25");
        Job dbJob = existingJob(company("FPT#25"), JobStatus.DRAFT, 0);
        dbJob.setTitle("Same Title");

        ReqUpdateJobDTO dto = ReqUpdateJobDTO.builder()
                .companyNo("FPT#25")
                .title("Same Title")
                .jobCategoryId(UUID.randomUUID())
                .build();

        when(companyRepository.findById("FPT#25")).thenReturn(Optional.of(company("FPT#25")));
        when(jobCategoryRepository.findById(dto.getJobCategoryId()))
                .thenReturn(Optional.of(JobCategory.builder().jobCategoryId(dto.getJobCategoryId()).name("Backend").build()));
        when(jobRepository.save(dbJob)).thenReturn(dbJob);

        jobService.updateJob(dto, dbJob);

        verify(jobEventProducer, never()).publishJobUpdated(any(), any());
    }

    /**
     * JobService.updateJob() has an explicit {@code ResourceNotFoundException(JOB_CATEGORY_NOT_FOUND)}
     * check after jobMapper.toEntity(), but JobMapper's own {@code map(UUID, JobCategoryRepository)}
     * already looks up the category and throws a plain RuntimeException first - the service-level
     * check is dead code for this path. Asserting actual behavior, not the unreachable intent.
     */
    @Test
    void updateJob_throwsRuntimeException_whenCategoryMissing() {
        authenticateAsCompany("FPT#25");
        Job dbJob = existingJob(company("FPT#25"), JobStatus.DRAFT, 0);
        UUID missingCategoryId = UUID.randomUUID();
        ReqUpdateJobDTO dto = ReqUpdateJobDTO.builder().companyNo("FPT#25").jobCategoryId(missingCategoryId).build();

        when(companyRepository.findById("FPT#25")).thenReturn(Optional.of(company("FPT#25")));
        when(jobCategoryRepository.findById(missingCategoryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.updateJob(dto, dbJob))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Job category not found");
    }

    // ---------------- updateStatus ----------------

    @Test
    void updateStatus_throwsNoPermission_whenNotAuthenticated() {
        Job job = existingJob(company("FPT#25"), JobStatus.DRAFT, 0);

        assertThatThrownBy(() -> jobService.updateStatus(job, JobStatus.PUBLISHED))
                .isInstanceOf(NoPermissionException.class);
    }

    @Test
    void updateStatus_throwsNoPermission_whenCompanyMismatch() {
        authenticateAsCompany("OTHER#1");
        Job job = existingJob(company("FPT#25"), JobStatus.DRAFT, 0);

        assertThatThrownBy(() -> jobService.updateStatus(job, JobStatus.PUBLISHED))
                .isInstanceOf(NoPermissionException.class);
    }

    @Test
    void updateStatus_throwsConflict_whenDraftExpired() {
        authenticateAsCompany("FPT#25");
        Job job = existingJob(company("FPT#25"), JobStatus.DRAFT, 0);
        job.setExpiresAt(Instant.now().minusSeconds(60));

        assertThatThrownBy(() -> jobService.updateStatus(job, JobStatus.PUBLISHED))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessage(org.workfitai.jobservice.util.MessageConstant.JOB_DRAFT_EXPIRED_NEEDS_UPDATING);
    }

    @Test
    void updateStatus_throwsConflict_whenSameStatus() {
        authenticateAsCompany("FPT#25");
        Job job = existingJob(company("FPT#25"), JobStatus.PUBLISHED, 0);

        assertThatThrownBy(() -> jobService.updateStatus(job, JobStatus.PUBLISHED))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessage(org.workfitai.jobservice.util.MessageConstant.JOB_STATUS_CONFLICT);
    }

    @Test
    void updateStatus_success() {
        authenticateAsCompany("FPT#25");
        Job job = existingJob(company("FPT#25"), JobStatus.DRAFT, 0);
        when(jobRepository.save(job)).thenReturn(job);

        ResModifyStatus result = jobService.updateStatus(job, JobStatus.PUBLISHED);

        assertThat(result.getStatus()).isEqualTo("PUBLISHED");
        verify(jobEventProducer).publishJobUpdated(eq(job), any());
    }

    // ---------------- deleteJob ----------------

    @Test
    void deleteJob_softDeletesAndPublishesEvent() {
        Job job = existingJob(company("FPT#25"), JobStatus.DRAFT, 0);
        when(jobRepository.findById(job.getJobId())).thenReturn(Optional.of(job));

        jobService.deleteJob(job.getJobId());

        assertThat(job.isDeleted()).isTrue();
        verify(jobRepository).save(job);
        verify(jobEventProducer).publishJobDeleted(job, "Soft deleted by user");
    }

    @Test
    void deleteJob_throwsResourceNotFound_whenMissing() {
        UUID jobId = UUID.randomUUID();
        when(jobRepository.findById(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.deleteJob(jobId))
                .isInstanceOf(org.springframework.data.rest.webmvc.ResourceNotFoundException.class);
    }

    // ---------------- updateStats ----------------

    @Test
    void updateStats_incrementsAndClosesJob_whenQuantityReached() {
        Job job = existingJob(company("FPT#25"), JobStatus.PUBLISHED, 4);
        job.setQuantity(5);
        when(jobRepository.findById(job.getJobId())).thenReturn(Optional.of(job));

        jobService.updateStats(job.getJobId(), 1, "INCREMENT");

        assertThat(job.getTotalApplications()).isEqualTo(5);
        assertThat(job.getStatus()).isEqualTo(JobStatus.CLOSED);
        verify(jobRepository).save(job);
    }

    @Test
    void updateStats_decrementsWithoutGoingNegative() {
        Job job = existingJob(company("FPT#25"), JobStatus.PUBLISHED, 1);
        when(jobRepository.findById(job.getJobId())).thenReturn(Optional.of(job));

        jobService.updateStats(job.getJobId(), 5, "DECREMENT");

        assertThat(job.getTotalApplications()).isEqualTo(0);
    }

    @Test
    void updateStats_noOp_whenJobIdNull() {
        jobService.updateStats(null, 1, "INCREMENT");

        verify(jobRepository, never()).findById(any());
    }

    @Test
    void updateStats_noOp_whenApplyCountZeroOrNegative() {
        jobService.updateStats(UUID.randomUUID(), 0, "INCREMENT");

        verify(jobRepository, never()).findById(any());
    }

    @Test
    void updateStats_noOp_whenJobNotFound() {
        UUID jobId = UUID.randomUUID();
        when(jobRepository.findById(jobId)).thenReturn(Optional.empty());

        jobService.updateStats(jobId, 1, "INCREMENT");

        verify(jobRepository, never()).save(any());
    }

    // ---------------- increaseViews ----------------

    @Test
    void increaseViews_incrementsViewCount() {
        Job job = existingJob(company("FPT#25"), JobStatus.PUBLISHED, 0);
        job.setViews(10L);
        when(jobRepository.findById(job.getJobId())).thenReturn(Optional.of(job));
        when(jobRepository.save(job)).thenReturn(job);

        Job result = jobService.increaseViews(job.getJobId());

        assertThat(result.getViews()).isEqualTo(11L);
    }

    @Test
    void increaseViews_throwsRuntimeException_whenJobMissing() {
        UUID jobId = UUID.randomUUID();
        when(jobRepository.findById(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.increaseViews(jobId))
                .isInstanceOf(RuntimeException.class);
    }

    // ---------------- getSimilarJobs ----------------

    @Test
    void getSimilarJobs_returnsAtMostFiveResults() {
        Job job = existingJob(company("FPT#25"), JobStatus.PUBLISHED, 0);
        job.setSkills(List.of());
        job.setLocation("Hanoi");
        job.setExperienceLevel(ExperienceLevel.JUNIOR);
        when(jobRepository.findById(job.getJobId())).thenReturn(Optional.of(job));

        List<Job> similar = new java.util.ArrayList<>();
        for (int i = 0; i < 7; i++) {
            similar.add(existingJob(company("FPT#25"), JobStatus.PUBLISHED, 0));
        }
        when(jobRepository.findAll(Mockito.<org.springframework.data.jpa.domain.Specification<Job>>any()))
                .thenReturn(similar);

        var result = jobService.getSimilarJobs(job.getJobId());

        assertThat(result).hasSizeLessThanOrEqualTo(5);
    }

    @Test
    void getSimilarJobs_throwsRuntimeException_whenJobMissing() {
        UUID jobId = UUID.randomUUID();
        when(jobRepository.findById(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.getSimilarJobs(jobId))
                .isInstanceOf(RuntimeException.class);
    }

    // ---------------- getFeaturedJobs ----------------

    @Test
    void getFeaturedJobs_returnsPaginatedResult() {
        Page<Job> page = new PageImpl<>(List.of(existingJob(company("FPT#25"), JobStatus.PUBLISHED, 0)));
        when(jobRepository.findAll(
                Mockito.<org.springframework.data.jpa.domain.Specification<Job>>any(),
                Mockito.<Pageable>any())).thenReturn(page);

        ResultPaginationDTO result = jobService.getFeaturedJobs(0);

        assertThat(result).isNotNull();
        assertThat((List<?>) result.getResult()).hasSize(1);
    }

    // ---------------- uploadJobBanner ----------------

    @Test
    void uploadJobBanner_throwsInvalidData_whenJobMissing() {
        UUID jobId = UUID.randomUUID();
        when(jobRepository.findById(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.uploadJobBanner(jobId, mock(org.springframework.web.multipart.MultipartFile.class)))
                .isInstanceOf(InvalidDataException.class);
    }

    // ---------------- fetchEmailMap ----------------

    @Test
    void fetchEmailMap_returnsEmptyMap_whenNoEligibleUsernames() {
        Job job = existingJob(company("FPT#25"), JobStatus.PUBLISHED, 0);
        job.setCreatedBy("system");

        var result = jobService.fetchEmailMap(List.of(job));

        assertThat(result).isEmpty();
        verify(userFeignClient, never()).getUsersByUsernames(any());
    }

    @Test
    void fetchEmailMap_mapsUsernameToEmail() {
        Job job = existingJob(company("FPT#25"), JobStatus.PUBLISHED, 0);
        job.setCreatedBy("alice");

        when(userFeignClient.getUsersByUsernames(List.of("alice"))).thenReturn(
                ResponseEntity.ok(List.of(UserInfoServeForJobResponse.builder().username("alice").email("alice@test.com").build())));

        var result = jobService.fetchEmailMap(List.of(job));

        assertThat(result).containsEntry("alice", "alice@test.com");
    }

    @Test
    void fetchEmailMap_returnsEmptyMap_whenFeignThrows() {
        Job job = existingJob(company("FPT#25"), JobStatus.PUBLISHED, 0);
        job.setCreatedBy("alice");

        when(userFeignClient.getUsersByUsernames(List.of("alice"))).thenThrow(new RuntimeException("down"));

        var result = jobService.fetchEmailMap(List.of(job));

        assertThat(result).isEmpty();
    }

    // ---------------- fetchHrJobExpirySettingsMap ----------------

    @Test
    void fetchHrJobExpirySettingsMap_usesFeignResult() {
        when(userFeignClient.getHrNotificationSettings("hr@test.com"))
                .thenReturn(HrNotificationSettingsDTO.builder().notifyOnJobExpiry(false).build());

        var result = jobService.fetchHrJobExpirySettingsMap(List.of("hr@test.com"));

        assertThat(result).containsEntry("hr@test.com", false);
    }

    @Test
    void fetchHrJobExpirySettingsMap_failsOpen_whenFeignThrows() {
        when(userFeignClient.getHrNotificationSettings("hr@test.com")).thenThrow(new RuntimeException("down"));

        var result = jobService.fetchHrJobExpirySettingsMap(List.of("hr@test.com"));

        assertThat(result).containsEntry("hr@test.com", true);
    }

    // ---------------- buildDTO / findExpiredJobsToClose / updateJobsAndEvents ----------------

    @Test
    void buildDTO_mapsJobFieldsToEventDTO() {
        Job job = existingJob(company("FPT#25"), JobStatus.PUBLISHED, 0);
        job.setCreatedBy("alice");

        var dto = jobService.buildDTO(job, "hr@test.com");

        assertThat(dto.getJobId()).isEqualTo(job.getId().toString());
        assertThat(dto.getCreatedBy()).isEqualTo("alice");
        assertThat(dto.getEmail()).isEqualTo("hr@test.com");
        assertThat(dto.getCompanyName()).isEqualTo("FPT Software");
    }

    @Test
    void findExpiredJobsToClose_delegatesToRepository() {
        when(jobRepository.findJobsToClose(any())).thenReturn(List.of());

        var result = jobService.findExpiredJobsToClose();

        assertThat(result).isEmpty();
        verify(jobRepository).findJobsToClose(any());
    }

    @Test
    void updateJobsAndEvents_savesJobsAndEvents() {
        List<Job> jobs = List.of(existingJob(company("FPT#25"), JobStatus.CLOSED, 0));
        List<org.workfitai.jobservice.model.OutboxExpiredJobEvent> events = List.of();

        jobService.updateJobsAndEvents(jobs, events);

        verify(jobRepository).saveAll(jobs);
        verify(outboxService).saveEvents(events);
    }
}
