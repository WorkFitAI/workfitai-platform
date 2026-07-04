package org.workfitai.jobservice.controller.HR;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.workfitai.jobservice.config.errors.InvalidDataException;
import org.workfitai.jobservice.model.Job;
import org.workfitai.jobservice.model.dto.request.Job.ReqJobDTO;
import org.workfitai.jobservice.model.dto.request.Job.ReqUpdateJobDTO;
import org.workfitai.jobservice.model.dto.response.Job.ResCreateJobDTO;
import org.workfitai.jobservice.model.dto.response.Job.ResJobDetailsForHrDTO;
import org.workfitai.jobservice.model.dto.response.Job.ResModifyStatus;
import org.workfitai.jobservice.model.dto.response.Job.ResUpdateJobDTO;
import org.workfitai.jobservice.model.dto.response.RestResponse;
import org.workfitai.jobservice.model.dto.response.ResultPaginationDTO;
import org.workfitai.jobservice.model.enums.JobStatus;
import org.workfitai.jobservice.service.iJobService;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobControllerTest {

    @Mock
    private iJobService jobService;
    @InjectMocks
    private JobController controller;

    @Test
    void getAllJob_delegatesToFetchAllForHr() {
        ResultPaginationDTO result = new ResultPaginationDTO();
        when(jobService.fetchAllForHr(any(), any())).thenReturn(result);

        RestResponse<ResultPaginationDTO> response = controller.getAllJob(null, PageRequest.of(0, 10));

        assertThat(response.getData()).isSameAs(result);
    }

    @Test
    void getJob_returnsJob_whenFound() throws InvalidDataException {
        UUID id = UUID.randomUUID();
        ResJobDetailsForHrDTO dto = ResJobDetailsForHrDTO.builder().build();
        when(jobService.fetchJobByIdForHr(id)).thenReturn(dto);

        RestResponse<ResJobDetailsForHrDTO> response = controller.getJob(id);

        assertThat(response.getData()).isSameAs(dto);
    }

    @Test
    void getJob_throwsInvalidData_whenNotFound() {
        UUID id = UUID.randomUUID();
        when(jobService.fetchJobByIdForHr(id)).thenReturn(null);

        assertThatThrownBy(() -> controller.getJob(id)).isInstanceOf(InvalidDataException.class);
    }

    @Test
    void create_delegatesToCreateJob() {
        ReqJobDTO dto = ReqJobDTO.builder().title("Java Backend Engineer").build();
        ResCreateJobDTO created = ResCreateJobDTO.builder().build();
        when(jobService.createJob(dto)).thenReturn(created);

        RestResponse<ResCreateJobDTO> response = controller.create(dto);

        assertThat(response.getData()).isSameAs(created);
    }

    @Test
    void update_delegatesToUpdateJob_whenJobExists() throws InvalidDataException {
        UUID jobId = UUID.randomUUID();
        Job dbJob = new Job();
        dbJob.setJobId(jobId);
        ReqUpdateJobDTO dto = ReqUpdateJobDTO.builder().jobId(jobId).build();
        when(jobService.getJobById(jobId)).thenReturn(Optional.of(dbJob));
        ResUpdateJobDTO updated = ResUpdateJobDTO.builder().build();
        when(jobService.updateJob(dto, dbJob)).thenReturn(updated);

        RestResponse<ResUpdateJobDTO> response = controller.update(dto);

        assertThat(response.getData()).isSameAs(updated);
    }

    @Test
    void update_throwsInvalidData_whenJobNotFound() {
        UUID jobId = UUID.randomUUID();
        ReqUpdateJobDTO dto = ReqUpdateJobDTO.builder().jobId(jobId).build();
        when(jobService.getJobById(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.update(dto)).isInstanceOf(InvalidDataException.class);
    }

    @Test
    void uploadBanner_delegatesToService() throws Exception {
        UUID jobId = UUID.randomUUID();
        var file = new MockMultipartFile("file", "banner.png", "image/png", new byte[] { 1 });
        when(jobService.uploadJobBanner(jobId, file)).thenReturn("https://cdn/banner.png");

        RestResponse<String> response = controller.uploadBanner(jobId, file);

        assertThat(response.getData()).isEqualTo("https://cdn/banner.png");
    }

    @Test
    void updateStatus_delegatesToService_whenJobExists() throws InvalidDataException {
        UUID jobId = UUID.randomUUID();
        Job dbJob = new Job();
        dbJob.setJobId(jobId);
        when(jobService.getJobById(jobId)).thenReturn(Optional.of(dbJob));
        ResModifyStatus modified = ResModifyStatus.builder().build();
        when(jobService.updateStatus(dbJob, JobStatus.PUBLISHED)).thenReturn(modified);

        RestResponse<ResModifyStatus> response = controller.updateStatus(jobId, JobStatus.PUBLISHED);

        assertThat(response.getData()).isSameAs(modified);
    }

    @Test
    void updateStatus_throwsInvalidData_whenJobNotFound() {
        UUID jobId = UUID.randomUUID();
        when(jobService.getJobById(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.updateStatus(jobId, JobStatus.PUBLISHED))
                .isInstanceOf(InvalidDataException.class);
    }
}
