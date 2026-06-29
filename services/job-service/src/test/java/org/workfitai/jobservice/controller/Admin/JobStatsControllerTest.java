package org.workfitai.jobservice.controller.Admin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workfitai.jobservice.model.dto.response.Job.JobPlatformStatsResponse;
import org.workfitai.jobservice.model.dto.response.RestResponse;
import org.workfitai.jobservice.service.impl.JobPlatformStatsService;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobStatsControllerTest {

    @Mock
    private JobPlatformStatsService statsService;
    @InjectMocks
    private JobStatsController controller;

    @Test
    void getPlatformStats_delegatesToService() {
        JobPlatformStatsResponse stats = new JobPlatformStatsResponse(
                Map.of(), 0L, 0L, 0L, 0L, Map.of(), Map.of(), Map.of(), List.of());
        when(statsService.getAdminStats()).thenReturn(stats);

        RestResponse<JobPlatformStatsResponse> response = controller.getPlatformStats();

        assertThat(response.getData()).isSameAs(stats);
    }
}
