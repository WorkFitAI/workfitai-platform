package org.workfitai.jobservice.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.workfitai.jobservice.model.dto.request.JobCategory.ReqCreateJobCategoryDTO;
import org.workfitai.jobservice.model.dto.request.JobCategory.ReqUpdateJobCategoryDTO;
import org.workfitai.jobservice.model.dto.response.JobCategory.JobCategoryStatisticDTO;
import org.workfitai.jobservice.model.dto.response.JobCategory.ResJobCategoryDTO;
import org.workfitai.jobservice.model.dto.response.RestResponse;
import org.workfitai.jobservice.model.dto.response.ResultPaginationDTO;
import org.workfitai.jobservice.service.iJobCategoryService;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobCategoryControllerTest {

    @Mock
    private iJobCategoryService jobCategoryService;
    @InjectMocks
    private JobCategoryController controller;

    @Test
    void getById_delegatesToService() {
        UUID id = UUID.randomUUID();
        ResJobCategoryDTO dto = new ResJobCategoryDTO();
        when(jobCategoryService.getById(id)).thenReturn(dto);

        RestResponse<ResJobCategoryDTO> response = controller.getById(id);

        assertThat(response.getData()).isSameAs(dto);
    }

    @Test
    void getAll_delegatesToService() {
        ResultPaginationDTO result = new ResultPaginationDTO();
        when(jobCategoryService.fetchAll(any(), any())).thenReturn(result);

        RestResponse<ResultPaginationDTO> response = controller.getAll(null, PageRequest.of(0, 10));

        assertThat(response.getData()).isSameAs(result);
    }

    @Test
    void getTopJobCategories_delegatesToService() {
        List<JobCategoryStatisticDTO> topCategories = List.of(new JobCategoryStatisticDTO());
        when(jobCategoryService.getTopJobCategories(10)).thenReturn(topCategories);

        RestResponse<List<JobCategoryStatisticDTO>> response = controller.getTopJobCategories(10);

        assertThat(response.getData()).isSameAs(topCategories);
    }

    @Test
    void create_delegatesToService() {
        ReqCreateJobCategoryDTO dto = new ReqCreateJobCategoryDTO();
        dto.setName("Engineering");
        ResJobCategoryDTO created = new ResJobCategoryDTO();
        when(jobCategoryService.create(dto)).thenReturn(created);

        RestResponse<ResJobCategoryDTO> response = controller.create(dto);

        assertThat(response.getData()).isSameAs(created);
    }

    @Test
    void update_delegatesToService() {
        ReqUpdateJobCategoryDTO dto = new ReqUpdateJobCategoryDTO();
        dto.setId(UUID.randomUUID());
        dto.setName("Updated");
        ResJobCategoryDTO updated = new ResJobCategoryDTO();
        when(jobCategoryService.update(dto)).thenReturn(updated);

        RestResponse<ResJobCategoryDTO> response = controller.update(dto);

        assertThat(response.getData()).isSameAs(updated);
    }

    @Test
    void delete_delegatesToService() {
        UUID id = UUID.randomUUID();

        controller.delete(id);

        verify(jobCategoryService).delete(id);
    }
}
