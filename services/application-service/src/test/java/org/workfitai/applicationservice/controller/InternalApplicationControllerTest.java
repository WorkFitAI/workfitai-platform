package org.workfitai.applicationservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.workfitai.applicationservice.dto.response.ActivePoolResponse;
import org.workfitai.applicationservice.service.IApplicationService;

@ExtendWith(MockitoExtension.class)
class InternalApplicationControllerTest {

    @Mock IApplicationService applicationService;

    @InjectMocks InternalApplicationController controller;

    @Test
    void getActivePool_returnsOkWithList() {
        ActivePoolResponse entry = ActivePoolResponse.builder()
                .jobId("job-1")
                .applicants(List.of(
                        ActivePoolResponse.ApplicantSnapshot.builder().username("user1").build(),
                        ActivePoolResponse.ApplicantSnapshot.builder().username("user2").build()))
                .build();
        when(applicationService.getActivePool()).thenReturn(List.of(entry));

        ResponseEntity<List<ActivePoolResponse>> resp = controller.getActivePool();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).hasSize(1);
        assertThat(resp.getBody().get(0).getJobId()).isEqualTo("job-1");
    }

    @Test
    void getActivePool_emptyList_returnsOkWithEmptyList() {
        when(applicationService.getActivePool()).thenReturn(List.of());

        ResponseEntity<List<ActivePoolResponse>> resp = controller.getActivePool();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isEmpty();
    }
}
