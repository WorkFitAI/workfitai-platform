package org.workfitai.jobservice.controller.Admin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.workfitai.jobservice.config.errors.InvalidDataException;
import org.workfitai.jobservice.model.dto.response.RestResponse;
import org.workfitai.jobservice.service.ElasticJobService;
import org.workfitai.jobservice.service.iJobService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JobControllerTest {

    @Mock
    private iJobService jobService;
    @Mock
    private ElasticJobService elasticJobService;
    @InjectMocks
    private JobController controller;

    @Test
    void delete_delegatesToServiceAndReturnsDeletedResponse() throws InvalidDataException {
        UUID jobId = UUID.randomUUID();

        RestResponse<Void> response = controller.delete(jobId);

        verify(jobService).deleteJob(jobId);
        assertThat(response.getMessage()).isEqualTo("Deleted");
    }

    @Test
    void delete_translatesResourceNotFoundToInvalidData() {
        UUID jobId = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("not found")).when(jobService).deleteJob(jobId);

        assertThatThrownBy(() -> controller.delete(jobId))
                .isInstanceOf(InvalidDataException.class);
    }
}
