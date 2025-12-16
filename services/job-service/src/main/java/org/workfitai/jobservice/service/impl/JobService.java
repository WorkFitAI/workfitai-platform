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
import org.workfitai.jobservice.config.errors.ResourceConflictException;
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
import org.workfitai.jobservice.service.iJobService;
import org.workfitai.jobservice.service.specifications.JobSpecifications;
import org.workfitai.jobservice.util.HtmlSanitizer;
import org.workfitai.jobservice.util.PaginationUtils;

import java.io.IOException;
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

    public JobService(JobRepository jobRepository, JobMapper jobMapper,
                      SkillRepository skillRepository, CompanyRepository companyRepository, CloudinaryService cloudinaryService) {
        this.jobRepository = jobRepository;
        this.jobMapper = jobMapper;
        this.skillRepository = skillRepository;
        this.companyRepository = companyRepository;
        this.cloudinaryService = cloudinaryService;
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
    public ResJobDetailsDTO fetchJobById(UUID id) {
        Optional<Job> jobOptional = getJobById(id);

        if (jobOptional.isEmpty()) return null;

        Job job = jobOptional.get();

        if (job.isDeleted() || !JobStatus.PUBLISHED.equals(job.getStatus())) {
            return null;
        }

        return jobMapper.toResJobDetailsDTO(job);
    }


    @Override
    public ResJobDetailsForHrDTO fetchJobByIdForHr(UUID id) {
        String currentUser = SecurityUtils.getCurrentUser();

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
        try {
            log.debug("Creating job with DTO: {}", jobDTO);
            Job job = jobMapper.toEntity(jobDTO, companyRepository, skillRepository);
            log.debug("Mapped job entity: {}", job);
            checkJobSkills(job, null);
            checkCompany(job, null);
            log.debug("Saving job to database");
            Job currentJob = this.jobRepository.save(job);
            log.debug("Job saved successfully with ID: {}", currentJob.getJobId());
            return jobMapper.toResCreateJobDTO(currentJob);
        } catch (Exception e) {
            log.error("Error creating job: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public ResUpdateJobDTO updateJob(ReqUpdateJobDTO jobDTO, Job dbJob) {
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
        return jobMapper.toResUpdateJobDTO(dbJob);
    }


    @Override
    public ResModifyStatus updateStatus(Job job, JobStatus status) {
        if (status == job.getStatus()) {
            throw new ResourceConflictException(JOB_STATUS_CONFLICT);
        }
        job.setStatus(status);
        this.jobRepository.save(job);

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
                    .filter(Objects::nonNull)   // tránh null id
                    .distinct()                  // loại bỏ trùng
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
                job.getExperienceLevel()
        );

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

}
