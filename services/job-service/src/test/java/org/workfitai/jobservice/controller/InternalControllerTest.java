package org.workfitai.jobservice.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workfitai.jobservice.model.dto.response.Job.HrmJobStatsResponse;
import org.workfitai.jobservice.model.dto.response.RestResponse;
import org.workfitai.jobservice.service.impl.JobPlatformStatsService;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalControllerTest {

    @Mock
    private JobPlatformStatsService statsService;
    @InjectMocks
    private InternalController controller;

    @Test
    void getCompanyStats_delegatesToService() {
        HrmJobStatsResponse stats = new HrmJobStatsResponse(0L, 0L, 0L, 0L, 0L, Map.of(), Map.of(), Map.of(), List.of());
        when(statsService.getCompanyStats("C-A")).thenReturn(stats);

        RestResponse<HrmJobStatsResponse> response = controller.getCompanyStats("C-A");

        assertThat(response.getData()).isSameAs(stats);
    }
}
