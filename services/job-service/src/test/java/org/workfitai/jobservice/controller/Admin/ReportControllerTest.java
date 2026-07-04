package org.workfitai.jobservice.controller.Admin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.workfitai.jobservice.model.dto.response.RestResponse;
import org.workfitai.jobservice.model.dto.response.ResultPaginationDTO;
import org.workfitai.jobservice.model.enums.EReportStatus;
import org.workfitai.jobservice.service.iReportService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    @Mock
    private iReportService reportService;
    @InjectMocks
    private ReportController controller;

    @Test
    void getReportsGrouped_delegatesToService() {
        ResultPaginationDTO result = new ResultPaginationDTO();
        when(reportService.fetchAllReportsGrouped(any(), any())).thenReturn(result);

        RestResponse<ResultPaginationDTO> response = controller.getReportsGrouped(null, PageRequest.of(0, 10));

        assertThat(response.getData()).isSameAs(result);
    }

    @Test
    void changeStatusByJobId_delegatesToServiceAndReturnsConfirmationMessage() {
        UUID jobId = UUID.randomUUID();

        RestResponse<String> response = controller.changeStatusByJobId(jobId, EReportStatus.RESOLVED);

        verify(reportService).changeStatus(jobId, EReportStatus.RESOLVED);
        assertThat(response.getData()).contains(jobId.toString()).contains("RESOLVED");
    }
}
