package org.workfitai.jobservice.service.abstraction;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.workfitai.jobservice.domain.Job;
import org.workfitai.jobservice.service.dto.request.ReqJobDTO;
import org.workfitai.jobservice.service.dto.request.ReqUpdateJobDTO;
import org.workfitai.jobservice.service.dto.response.ResCreateJobDTO;
import org.workfitai.jobservice.service.dto.response.ResJobDTO;
import org.workfitai.jobservice.service.dto.response.ResUpdateJobDTO;
import org.workfitai.jobservice.service.dto.response.ResultPaginationDTO;

import java.util.Optional;
import java.util.UUID;

public interface iJobService {
    ResultPaginationDTO fetchAll(Specification<Job> spec, Pageable pageable);

    ResJobDTO fetchJobById(UUID id);

    Optional<Job> getJobById(UUID id);

    ResCreateJobDTO createJob(ReqJobDTO jobDTO);

    ResUpdateJobDTO updateJob(ReqUpdateJobDTO jobDTO, Job dbJob);

    void deleteJob(UUID jobId);
}
