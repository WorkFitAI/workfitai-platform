package org.workfitai.jobservice.service.impl;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.workfitai.jobservice.config.errors.InvalidDataException;
import org.workfitai.jobservice.config.errors.NoPermissionException;
import org.workfitai.jobservice.config.errors.ResourceConflictException;
import org.workfitai.jobservice.dto.kafka.NotificationEvent;
import org.workfitai.jobservice.messaging.NotificationProducer;
import org.workfitai.jobservice.model.Company;
import org.workfitai.jobservice.model.Job;
import org.workfitai.jobservice.model.Skill;
import org.workfitai.jobservice.model.dto.request.Job.ReqJobDTO;
import org.workfitai.jobservice.model.dto.request.Job.ReqUpdateJobDTO;
import org.workfitai.jobservice.model.dto.response.Job.*;
import org.workfitai.jobservice.model.dto.response.ResultPaginationDTO;
import org.workfitai.jobservice.model.enums.JobStatus;
import org.workfitai.jobservice.model.mapper.JobMapper;
import org.workfitai.jobservice.repository.CompanyRepository;
import org.workfitai.jobservice.repository.JobRepository;
import org.workfitai.jobservice.repository.SkillRepository;
import org.workfitai.jobservice.security.SecurityUtils;
import org.workfitai.jobservice.service.CloudinaryService;
import org.workfitai.jobservice.service.JobEventProducer;
import org.workfitai.jobservice.service.iJobService;
import org.workfitai.jobservice.service.specifications.JobSpecifications;
import org.workfitai.jobservice.util.HtmlSanitizer;
import org.workfitai.jobservice.util.PaginationUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.workfitai.jobservice.util.MessageConstant.JOB_NOT_FOUND;
import static org.workfitai.jobservice.util.MessageConstant.JOB_STATUS_CONFLICT;

@Service
@Slf4j
public class JobService implements iJobService {
    private final JobRepository jobRepository;
    private final JobMapper jobMapper;

    private final SkillRepository skillRepository;

    private final CompanyRepository companyRepository;

    private final CloudinaryService cloudinaryService;

    private final JobEventProducer jobEventProducer;

    private final NotificationProducer notificationProducer;

    public JobService(JobRepository jobRepository, JobMapper jobMapper,
            SkillRepository skillRepository, CompanyRepository companyRepository,
            CloudinaryService cloudinaryService, JobEventProducer jobEventProducer,
            NotificationProducer notificationProducer) {
        this.jobRepository = jobRepository;
        this.jobMapper = jobMapper;
        this.skillRepository = skillRepository;
        this.companyRepository = companyRepository;
        this.cloudinaryService = cloudinaryService;
        this.jobEventProducer = jobEventProducer;
        this.notificationProducer = notificationProducer;
    }

    @Override
    public ResultPaginationDTO fetchAll(Specification<Job> spec, Pageable pageable) {
        // 1. Nếu spec null => khởi tạo Specification mở rộng (unrestricted)
        Specification<Job> baseSpec = (spec != null) ? spec : Specification.unrestricted();

        // 2. Kết hợp thêm các specification cố định: statusPublished và isNoDeleted
        Specification<Job> finalSpec = baseSpec
                .and(JobSpecifications.statusPublished())
                .and(JobSpecifications.isNoDeleted());

        // 3. Lấy dữ liệu từ repository
        Page<Job> pageJob = jobRepository.findAll(finalSpec, pageable);

        // 4. Chuyển Page<Job> sang ResultPaginationDTO dùng PaginationUtils
        return PaginationUtils.toResultPaginationDTO(pageJob, jobMapper::toResJobDTO);
    }

    @Override
    public ResultPaginationDTO fetchAllForHr(Specification<Job> spec, Pageable pageable) {
        // 1. Nếu spec null => khởi tạo Specification mở rộng (unrestricted)
        Specification<Job> baseSpec = (spec != null) ? spec : Specification.unrestricted();

        // 2. Kết hợp thêm các specification cố định: ownedByCurrentUser và isNoDeleted
        Specification<Job> finalSpec = baseSpec
                .and(JobSpecifications.ownedByCurrentUser())
                .and(JobSpecifications.isNoDeleted());

        // 3. Lấy dữ liệu từ repository
        Page<Job> pageJob = jobRepository.findAll(finalSpec, pageable);

        // 4. Chuyển Page<Job> sang ResultPaginationDTO dùng PaginationUtils
        return PaginationUtils.toResultPaginationDTO(pageJob, jobMapper::toResJobDTO);
    }

    @Override
    public ResultPaginationDTO fetchAllForAdmin(Specification<Job> spec, Pageable pageable) {
        Page<Job> pageJob = jobRepository.findAll(spec, pageable);
        return PaginationUtils.toResultPaginationDTO(pageJob, jobMapper::toResJobDTO);
    }

    @Override
    public ResultPaginationDTO fetchJobsByCompany(String companyId, Pageable pageable) {
        Specification<Job> spec = Specification.where(JobSpecifications.hasCompanyId(companyId))
                .and(JobSpecifications.statusPublished())
                .and(JobSpecifications.isNoDeleted());

        Page<Job> pageJob = jobRepository.findAll(spec, pageable);
        return PaginationUtils.toResultPaginationDTO(pageJob, jobMapper::toResJobDTO);
    }

    @Override
    public ResJobDetailsDTO fetchJobById(UUID id) {
        Optional<Job> jobOptional = getJobById(id);

        if (jobOptional.isEmpty())
            return null;

        Job job = jobOptional.get();

        if (job.isDeleted() || !JobStatus.PUBLISHED.equals(job.getStatus())) {
            return null;
        }

        return jobMapper.toResJobDetailsDTO(job);
    }

    @Override
    public ResJobDetailsForHrDTO fetchJobByIdForHr(UUID id) {
        String currentUser = SecurityUtils.getCurrentUser();
        String validCompanyNo = SecurityUtils.getValidCompanyNo();

        companyRepository.findById(validCompanyNo)
                .orElseThrow(() -> new NoPermissionException("You don't have permission to get the job!"));

        Job job = jobRepository.findByIdAndCreatedBy(id, currentUser)
                .orElseThrow(() -> new ResourceNotFoundException(JOB_NOT_FOUND));

        if (job.isDeleted()) {
            return null;
        }

        return jobMapper.toResJobDetailsForHrDTO(job);
    }

    public ResJobDetailsForAdminDTO fetchJobByIdForAdmin(UUID id) {
        Optional<Job> jobOptional = getJobById(id);

        Job job = jobOptional.get();

        return jobMapper.toResJobDetailsForAdminDTO(job);
    }

    @Override
    public Optional<Job> getJobById(UUID id) {
        return this.jobRepository.findById(id);
    }

    @Override
    public ResCreateJobDTO createJob(ReqJobDTO jobDTO) {
        // String validCompanyNo = SecurityUtils.getValidCompanyNo();

        // companyRepository.findById(validCompanyNo).orElseThrow(
        // () -> new NoPermissionException("You don't have permission to create job with
        // this company"));
        try {
            log.debug("Creating job with DTO: {}", jobDTO);
            Job job = jobMapper.toEntity(jobDTO, companyRepository, skillRepository);
            log.debug("Mapped job entity: {}", job);
            checkJobSkills(job, null);
            checkCompany(job, null);
            log.debug("Saving job to database");
            Job currentJob = this.jobRepository.save(job);
            log.debug("Job saved successfully with ID: {}", currentJob.getJobId());

            // Publish job created event to Kafka
            jobEventProducer.publishJobCreated(currentJob);

            // Send notification to HR
            sendJobCreatedNotification(currentJob);

            return jobMapper.toResCreateJobDTO(currentJob);
        } catch (Exception e) {
            log.error("Error creating job: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Send email notification to HR when job is created
     */
    private void sendJobCreatedNotification(Job job) {
        try {
            // Get HR email from SecurityContext (current authenticated user who created the
            // job)
            String hrEmail = SecurityContextHolder.getContext().getAuthentication().getName(); // Username is email

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("jobTitle", job.getTitle());
            metadata.put("jobId", job.getJobId().toString());
            metadata.put("companyName", job.getCompany() != null ? job.getCompany().getName() : "");
            metadata.put("status", job.getStatus().toString());
            metadata.put("location", job.getLocation());
            metadata.put("employmentType", job.getEmploymentType() != null ? job.getEmploymentType().toString() : "");

            NotificationEvent event = NotificationEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("JOB_CREATED")
                    .timestamp(Instant.now())
                    .recipientEmail(hrEmail)
                    .recipientRole("HR")
                    .templateType("job-created")
                    .sendEmail(true)
                    .createInAppNotification(false)
                    .referenceId(job.getJobId().toString())
                    .referenceType("JOB")
                    .metadata(metadata)
                    .build();

            notificationProducer.send(event);
            log.info("Sent job created notification for job: {} to {}", job.getJobId(), hrEmail);
        } catch (Exception e) {
            log.error("Failed to send job created notification for job: {}", job.getJobId(), e);
        }
    }

    @Override
    public ResUpdateJobDTO updateJob(ReqUpdateJobDTO jobDTO, Job dbJob) {
        String validCompanyNo = SecurityUtils.getValidCompanyNo();
        companyRepository.findById(validCompanyNo).orElseThrow(
                () -> new NoPermissionException("You don't have permission to update job with this company"));

        // Clone the old job for change detection
        Job oldJob = cloneJobForComparison(dbJob);

        Job job = jobMapper.toEntity(jobDTO, companyRepository, skillRepository);
        checkJobSkills(job, dbJob);
        checkCompany(job, dbJob);

        dbJob.setTitle(HtmlSanitizer.sanitize(job.getTitle()));
        dbJob.setDescription(HtmlSanitizer.sanitize(job.getDescription()));
        dbJob.setShortDescription(HtmlSanitizer.sanitize(job.getShortDescription()));
        dbJob.setLocation(HtmlSanitizer.sanitize(job.getLocation()));
        dbJob.setSalaryMin(job.getSalaryMin());
        dbJob.setSalaryMax(job.getSalaryMax());
        dbJob.setExperienceLevel(job.getExperienceLevel());
        dbJob.setEducationLevel(HtmlSanitizer.sanitize(job.getEducationLevel()));
        dbJob.setCurrency(HtmlSanitizer.sanitize(job.getCurrency()));
        dbJob.setRequirements(HtmlSanitizer.sanitize(job.getRequirements()));
        dbJob.setResponsibilities(HtmlSanitizer.sanitize(job.getResponsibilities()));
        dbJob.setBenefits(HtmlSanitizer.sanitize(job.getBenefits()));
        dbJob.setEmploymentType(job.getEmploymentType());
        dbJob.setQuantity(job.getQuantity());
        dbJob.setExpiresAt(job.getExpiresAt());

        this.jobRepository.save(dbJob);

        // Publish job updated event with changes
        Map<String, Object> changes = detectChanges(oldJob, dbJob);
        if (!changes.isEmpty()) {
            jobEventProducer.publishJobUpdated(dbJob, changes);
        }

        return jobMapper.toResUpdateJobDTO(dbJob);
    }

    @Override
    public ResModifyStatus updateStatus(Job job, JobStatus status) {
        String validCompanyNo = SecurityUtils.getValidCompanyNo();

        companyRepository.findById(validCompanyNo).orElseThrow(
                () -> new NoPermissionException("You don't have permission to update stats with this company"));

        if (status == job.getStatus()) {
            throw new ResourceConflictException(JOB_STATUS_CONFLICT);
        }

        JobStatus oldStatus = job.getStatus();
        job.setStatus(status);
        this.jobRepository.save(job);

        // Publish update event for status change
        Map<String, Object> changes = new HashMap<>();
        changes.put("status", Map.of("old", oldStatus.toString(), "new", status.toString()));
        jobEventProducer.publishJobUpdated(job, changes);

        return new ResModifyStatus().builder()
                .status(String.valueOf(job.getStatus()))
                .lastModifiedDate(job.getLastModifiedDate())
                .build();
    }

    @Override
    public void deleteJob(UUID jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException(JOB_NOT_FOUND));

        job.setDeleted(true);
        this.jobRepository.save(job);

        // Publish job deleted event
        jobEventProducer.publishJobDeleted(job, "Soft deleted by user");
    }

    private void checkCompany(Job job, Job dbJob) {
        if (job.getCompany() != null) {
            String companyId = job.getCompany().getId();
            Optional<Company> company = this.companyRepository.findById(companyId);
            if (company.isPresent()) {
                if (dbJob != null) {
                    dbJob.setCompany(company.get()); // update company
                } else {
                    job.setCompany(company.get()); // create company
                }
            }
        }
    }

    private void checkJobSkills(Job job, Job dbJob) {
        if (job.getSkills() != null) {
            List<UUID> reqSkills = job.getSkills().stream()
                    .map(Skill::getId)
                    .filter(Objects::nonNull) // tránh null id
                    .distinct() // loại bỏ trùng
                    .collect(Collectors.toList());

            if (!reqSkills.isEmpty()) {
                List<Skill> dbSkills = this.skillRepository.findByIdIn(reqSkills);
                if (dbJob != null) {
                    dbJob.setSkills(dbSkills); // update skills
                } else {
                    job.setSkills(dbSkills); // create skills
                }
            }
        }
    }

    @Transactional
    public void updateStats(UUID jobId, int applyCount) {
        if (jobId == null || applyCount <= 0) {
            log.warn("Invalid jobId or applyCount, skipping update");
            return;
        }

        // Lock row để tránh race condition khi nhiều người apply cùng lúc
        Optional<Job> optionalJob = jobRepository.findByIdForUpdate(jobId);
        if (optionalJob.isEmpty()) {
            log.warn("Job not found with jobId: {}", jobId);
            return;
        }

        Job job = optionalJob.get();

        int currentTotal = job.getTotalApplications();

        job.setTotalApplications(currentTotal + applyCount);

        if (job.getTotalApplications() == job.getQuantity()) {
            job.setStatus(JobStatus.CLOSED);
        }

        jobRepository.save(job);

        log.info("Updated totalApplications for jobId {}: {}", jobId, job.getTotalApplications());
    }

    @Transactional
    public Job increaseViews(UUID jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found"));

        job.setViews(job.getViews() + 1);

        return jobRepository.save(job);
    }

    @Override
    public List<ResJobDTO> getSimilarJobs(UUID jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found"));

        List<UUID> skillIds = job.getSkills()
                .stream().map(Skill::getSkillId).toList();

        List<Job> list = jobRepository.findSimilarJobs(
                jobId,
                skillIds,
                job.getLocation(),
                job.getTitle(),
                job.getExperienceLevel());

        Collections.shuffle(list);

        return list.stream()
                .limit(5)
                .map(jobMapper::toResJobDTO)
                .toList();
    }

    @Override
    public ResultPaginationDTO getFeaturedJobs(int page) {
        Pageable pageable = PageRequest.of(page, 4);

        Page<Job> pageJob = jobRepository.findFeaturedJobs(pageable);
        return PaginationUtils.toResultPaginationDTO(pageJob, jobMapper::toResJobDTO);
    }

    @Transactional
    public String uploadJobBanner(UUID jobId, MultipartFile bannerFile) throws InvalidDataException, IOException {
        Job dbJob = jobRepository.findById(jobId)
                .orElseThrow(() -> new InvalidDataException(JOB_NOT_FOUND));

        String bannerUrl = cloudinaryService.uploadFile(bannerFile, dbJob.getCompany().getCompanyNo());
        dbJob.setBannerUrl(bannerUrl);
        jobRepository.save(dbJob);

        return bannerUrl;
    }

    /**
     * Clone job for comparison before update
     */
    private Job cloneJobForComparison(Job job) {
        Job clone = new Job();
        clone.setJobId(job.getJobId());
        clone.setTitle(job.getTitle());
        clone.setDescription(job.getDescription());
        clone.setShortDescription(job.getShortDescription());
        clone.setLocation(job.getLocation());
        clone.setSalaryMin(job.getSalaryMin());
        clone.setSalaryMax(job.getSalaryMax());
        clone.setExperienceLevel(job.getExperienceLevel());
        clone.setEducationLevel(job.getEducationLevel());
        clone.setCurrency(job.getCurrency());
        clone.setRequirements(job.getRequirements());
        clone.setResponsibilities(job.getResponsibilities());
        clone.setBenefits(job.getBenefits());
        clone.setEmploymentType(job.getEmploymentType());
        clone.setQuantity(job.getQuantity());
        clone.setExpiresAt(job.getExpiresAt());
        clone.setStatus(job.getStatus());
        return clone;
    }

    /**
     * Detect changes between old and new job
     */
    private Map<String, Object> detectChanges(Job oldJob, Job newJob) {
        Map<String, Object> changes = new HashMap<>();

        if (!Objects.equals(oldJob.getTitle(), newJob.getTitle())) {
            changes.put("title", Map.of("old", oldJob.getTitle(), "new", newJob.getTitle()));
        }
        if (!Objects.equals(oldJob.getDescription(), newJob.getDescription())) {
            changes.put("description", "updated");
        }
        if (!Objects.equals(oldJob.getShortDescription(), newJob.getShortDescription())) {
            changes.put("shortDescription", "updated");
        }
        if (!Objects.equals(oldJob.getLocation(), newJob.getLocation())) {
            changes.put("location", Map.of("old", oldJob.getLocation(), "new", newJob.getLocation()));
        }
        if (!Objects.equals(oldJob.getSalaryMin(), newJob.getSalaryMin())) {
            changes.put("salaryMin", Map.of("old", oldJob.getSalaryMin(), "new", newJob.getSalaryMin()));
        }
        if (!Objects.equals(oldJob.getSalaryMax(), newJob.getSalaryMax())) {
            changes.put("salaryMax", Map.of("old", oldJob.getSalaryMax(), "new", newJob.getSalaryMax()));
        }
        if (!Objects.equals(oldJob.getExperienceLevel(), newJob.getExperienceLevel())) {
            changes.put("experienceLevel",
                    Map.of("old", oldJob.getExperienceLevel(), "new", newJob.getExperienceLevel()));
        }
        if (!Objects.equals(oldJob.getEmploymentType(), newJob.getEmploymentType())) {
            changes.put("employmentType", Map.of("old", oldJob.getEmploymentType(), "new", newJob.getEmploymentType()));
        }
        if (!Objects.equals(oldJob.getStatus(), newJob.getStatus())) {
            changes.put("status", Map.of("old", oldJob.getStatus().toString(), "new", newJob.getStatus().toString()));
        }

        return changes;
    }

}
