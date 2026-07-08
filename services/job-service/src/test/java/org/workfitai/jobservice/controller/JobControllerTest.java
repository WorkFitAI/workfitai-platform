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
import static org.mockito.Mockito.*;

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
        verify(jobService).fetchJobById(id);
        verifyNoMoreInteractions(jobService);

        assertThat(response).isNotNull();
        assertThat(response.getData()).isSameAs(dto);
    }

    @Test
    void getJob_throwsInvalidData_whenJobNotFound() {
        UUID id = UUID.randomUUID();

        when(jobService.fetchJobById(id)).thenReturn(null);

        assertThatThrownBy(() -> controller.getJob(id))
                .isInstanceOf(InvalidDataException.class);

        verify(jobService).increaseViews(id);
        verify(jobService).fetchJobById(id);
        verifyNoMoreInteractions(jobService);
    }

    @Test
    void getAllJob_returnsPaginationResult() {
        ResultPaginationDTO result = new ResultPaginationDTO();

        when(jobService.fetchAll(any(), any(), any()))
                .thenReturn(result);

        PageRequest pageable = PageRequest.of(0, 10);

        RestResponse<ResultPaginationDTO> response = controller.getAllJob(null, "java", pageable);

        verify(jobService).fetchAll(null, "java", pageable);
        verifyNoMoreInteractions(jobService);

        assertThat(response).isNotNull();
        assertThat(response.getData()).isSameAs(result);
    }

    @Test
    void getFeaturedJobs_returnsFeaturedJobs() {
        ResultPaginationDTO result = new ResultPaginationDTO();

        when(jobService.getFeaturedJobs(0)).thenReturn(result);

        RestResponse<ResultPaginationDTO> response = controller.getFeaturedJobs(0);

        verify(jobService).getFeaturedJobs(0);
        verifyNoMoreInteractions(jobService);

        assertThat(response).isNotNull();
        assertThat(response.getData()).isSameAs(result);
    }

    @Test
    void getFeaturedJobs_returnsFeaturedJobs_forAnotherPage() {
        ResultPaginationDTO result = new ResultPaginationDTO();

        when(jobService.getFeaturedJobs(2)).thenReturn(result);

        RestResponse<ResultPaginationDTO> response = controller.getFeaturedJobs(2);

        verify(jobService).getFeaturedJobs(2);
        verifyNoMoreInteractions(jobService);

        assertThat(response.getData()).isSameAs(result);
    }

    @Test
    void getSimilarJobs_returnsSimilarJobs() {
        UUID jobId = UUID.randomUUID();

        List<ResJobDTO> jobs = List.of(
                ResJobDTO.builder().build(),
                ResJobDTO.builder().build());

        when(jobService.getSimilarJobs(jobId)).thenReturn(jobs);

        RestResponse<List<ResJobDTO>> response = controller.getSimilarJobs(jobId);

        verify(jobService).getSimilarJobs(jobId);
        verifyNoMoreInteractions(jobService);

        assertThat(response).isNotNull();
        assertThat(response.getData()).isSameAs(jobs);
        assertThat(response.getData()).hasSize(2);
    }

    @Test
    void getSimilarJobs_returnsEmptyList() {
        UUID jobId = UUID.randomUUID();

        when(jobService.getSimilarJobs(jobId)).thenReturn(List.of());

        RestResponse<List<ResJobDTO>> response = controller.getSimilarJobs(jobId);

        verify(jobService).getSimilarJobs(jobId);
        verifyNoMoreInteractions(jobService);

        assertThat(response.getData()).isEmpty();
    }
}