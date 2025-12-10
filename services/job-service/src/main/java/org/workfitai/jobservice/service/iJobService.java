package org.workfitai.jobservice.service;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.multipart.MultipartFile;
import org.workfitai.jobservice.config.errors.InvalidDataException;
import org.workfitai.jobservice.model.Job;
import org.workfitai.jobservice.model.dto.request.ReqJobDTO;
import org.workfitai.jobservice.model.dto.request.ReqUpdateJobDTO;
import org.workfitai.jobservice.model.dto.response.*;
import org.workfitai.jobservice.model.enums.JobStatus;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface iJobService {
    ResultPaginationDTO fetchAll(Specification<Job> spec, Pageable pageable);

    ResJobDetailsDTO fetchJobById(UUID id);

    ResJobDetailsForHrDTO fetchJobByIdForHr(UUID id);

    Optional<Job> getJobById(UUID id);

    ResCreateJobDTO createJob(ReqJobDTO jobDTO);

    ResUpdateJobDTO updateJob(ReqUpdateJobDTO jobDTO, Job dbJob);

    ResModifyStatus updateStatus(Job job, JobStatus status);

    void deleteJob(UUID jobId);

    Job increaseViews(UUID jobId);

    List<ResJobDTO> getSimilarJobs(UUID jobId);

    ResultPaginationDTO getFeaturedJobs(int page);

    String uploadJobBanner(UUID jobId, MultipartFile bannerFile) throws InvalidDataException, IOException;
}
