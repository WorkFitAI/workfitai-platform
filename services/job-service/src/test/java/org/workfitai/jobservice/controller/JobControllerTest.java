package org.workfitai.jobservice.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.workfitai.jobservice.config.errors.InvalidDataException;
import org.workfitai.jobservice.model.dto.response.Job.ResJobDTO;
import org.workfitai.jobservice.model.dto.response.Job.ResJobDetailsDTO;
import org.workfitai.jobservice.model.dto.response.RestResponse;
import org.workfitai.jobservice.model.dto.response.ResultPaginationDTO;
import org.workfitai.jobservice.service.iJobService;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobControllerTest {

    @Mock
    private iJobService jobService;
    @InjectMocks
    private JobController controller;

    @Test
    void getJob_increasesViewsAndReturnsDetails_whenFound() throws InvalidDataException {
        UUID id = UUID.randomUUID();
        ResJobDetailsDTO dto = ResJobDetailsDTO.builder().build();
        when(jobService.fetchJobById(id)).thenReturn(dto);

        RestResponse<ResJobDetailsDTO> response = controller.getJob(id);

        verify(jobService).increaseViews(id);
        assertThat(response.getData()).isSameAs(dto);
    }

    @Test
    void getJob_throwsInvalidData_whenNotFound() {
        UUID id = UUID.randomUUID();
        when(jobService.fetchJobById(id)).thenReturn(null);

        assertThatThrownBy(() -> controller.getJob(id)).isInstanceOf(InvalidDataException.class);
        verify(jobService).increaseViews(id);
    }

    @Test
    void getAllJob_delegatesToFetchAll() {
        ResultPaginationDTO result = new ResultPaginationDTO();
        when(jobService.fetchAll(any(), any())).thenReturn(result);

        RestResponse<ResultPaginationDTO> response = controller.getAllJob(null, PageRequest.of(0, 10));

        assertThat(response.getData()).isSameAs(result);
    }

    @Test
    void getFeaturedJobs_delegatesToService() {
        ResultPaginationDTO result = new ResultPaginationDTO();
        when(jobService.getFeaturedJobs(0)).thenReturn(result);

        RestResponse<ResultPaginationDTO> response = controller.getFeaturedJobs(0);

        assertThat(response.getData()).isSameAs(result);
    }

    @Test
    void getSimilarJobs_delegatesToService() {
        UUID jobId = UUID.randomUUID();
        List<ResJobDTO> similar = List.of(ResJobDTO.builder().build());
        when(jobService.getSimilarJobs(jobId)).thenReturn(similar);

        RestResponse<List<ResJobDTO>> response = controller.getSimilarJobs(jobId);

        assertThat(response.getData()).isSameAs(similar);
    }
}
