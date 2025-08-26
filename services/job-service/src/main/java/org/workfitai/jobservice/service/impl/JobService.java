package org.workfitai.jobservice.service.impl;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.workfitai.jobservice.domain.Company;
import org.workfitai.jobservice.domain.Job;
import org.workfitai.jobservice.domain.Skill;
import org.workfitai.jobservice.domain.enums.JobStatus;
import org.workfitai.jobservice.repository.CompanyRepository;
import org.workfitai.jobservice.repository.JobRepository;
import org.workfitai.jobservice.repository.SkillRepository;
import org.workfitai.jobservice.service.dto.request.ReqJobDTO;
import org.workfitai.jobservice.service.dto.request.ReqUpdateJobDTO;
import org.workfitai.jobservice.service.dto.response.*;
import org.workfitai.jobservice.service.iJobService;
import org.workfitai.jobservice.service.mapper.JobMapper;
import org.workfitai.jobservice.web.errors.JobConflictException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class JobService implements iJobService {
    private final JobRepository jobRepository;
    private final JobMapper jobMapper;

    private final SkillRepository skillRepository;

    private final CompanyRepository companyRepository;

    public JobService(JobRepository jobRepository, JobMapper jobMapper,
                      SkillRepository skillRepository, CompanyRepository companyRepository) {
        this.jobRepository = jobRepository;
        this.jobMapper = jobMapper;
        this.skillRepository = skillRepository;
        this.companyRepository = companyRepository;
    }

    @Override
    public ResultPaginationDTO fetchAll(Specification<Job> spec, Pageable pageable) {
        Page<Job> pageJob = this.jobRepository.findAll(spec, pageable);
        Page<ResJobDTO> pageJobDTO = pageJob.map(jobMapper::toResJobDTO);

        ResultPaginationDTO rs = new ResultPaginationDTO();

        ResultPaginationDTO.Meta mt = new ResultPaginationDTO.Meta();
        mt.setPage(pageable.getPageNumber() + 1);
        mt.setPageSize(pageable.getPageSize());

        mt.setPages(pageJob.getTotalPages());
        mt.setTotal(pageJob.getTotalElements());

        rs.setMeta(mt);
        rs.setResult(pageJobDTO.getContent());

        return rs;
    }

    @Override
    public ResJobDTO fetchJobById(UUID id) {
        Optional jobOptional = this.jobRepository.findById(id);
        if (jobOptional.isPresent()) {
            return jobMapper.toResJobDTO((Job) jobOptional.get());
        }
        return null;
    }

    @Override
    public Optional<Job> getJobById(UUID id) {
        return this.jobRepository.findById(id);
    }

    @Override
    public ResCreateJobDTO createJob(ReqJobDTO jobDTO) {
        Job job = jobMapper.toEntity(jobDTO, companyRepository, skillRepository);
        checkJobSkills(job, null);
        checkCompany(job, null);
        Job currentJob = this.jobRepository.save(job);
        return jobMapper.toResCreateJobDTO(currentJob);
    }

    @Override
    public ResUpdateJobDTO updateJob(ReqUpdateJobDTO jobDTO, Job dbJob) {
        Job job = jobMapper.toEntity(jobDTO, companyRepository, skillRepository);
        checkJobSkills(job, dbJob);
        checkCompany(job, dbJob);

        dbJob.setTitle(job.getTitle());
        dbJob.setDescription(job.getDescription());
        dbJob.setLocation(job.getLocation());
        dbJob.setSalaryMin(job.getSalaryMin());
        dbJob.setSalaryMax(job.getSalaryMax());
        dbJob.setExperienceLevel(job.getExperienceLevel());
        dbJob.setEducationLevel(job.getEducationLevel());
        dbJob.setCurrency(job.getCurrency());
        dbJob.setEmploymentType(job.getEmploymentType());
        dbJob.setQuantity(job.getQuantity());
        dbJob.setExpiresAt(job.getExpiresAt());

        this.jobRepository.save(dbJob);
        return jobMapper.toResUpdateJobDTO(dbJob);
    }

    @Override
    public ResModifyStatus updateStatus(Job job, JobStatus status) {
        if (status == job.getStatus()) {
            throw new JobConflictException("Job status conflict");
        }
        job.setStatus(status);
        this.jobRepository.save(job);

        return new ResModifyStatus().builder()
                .status(String.valueOf(job.getStatus()))
                .updatedAt(job.getLastModifiedDate())
                .build();
    }

    @Override
    public void deleteJob(UUID jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));

        if (job.getStatus() == JobStatus.PUBLISHED) {
            throw new JobConflictException("Cannot delete a PUBLISHED job");
        }

        jobRepository.delete(job);
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
            List<UUID> reqSkills = job.getSkills()
                    .stream().map(Skill::getId)
                    .collect(Collectors.toList());
            List<Skill> dbSkills = this.skillRepository.findByIdIn(reqSkills);
            if (dbJob != null) {
                dbJob.setSkills(dbSkills); // update skills
            } else {
                job.setSkills(dbSkills); // create skills
            }
        }
    }

}
