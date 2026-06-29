package org.workfitai.jobservice.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.workfitai.jobservice.model.dto.response.Company.ResCompanyDTO;
import org.workfitai.jobservice.model.dto.response.RestResponse;
import org.workfitai.jobservice.model.dto.response.ResultPaginationDTO;
import org.workfitai.jobservice.service.iCompanyService;
import org.workfitai.jobservice.service.iJobService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyControllerTest {

    @Mock
    private iCompanyService companyService;
    @Mock
    private iJobService jobService;
    @InjectMocks
    private CompanyController controller;

    @Test
    void getCompany_returnsCompanyFromService() {
        ResCompanyDTO dto = ResCompanyDTO.builder().companyNo("C-A").name("FPT").build();
        when(companyService.getById("C-A")).thenReturn(dto);

        RestResponse<ResCompanyDTO> response = controller.getCompany("C-A");

        assertThat(response.getData()).isSameAs(dto);
    }

    @Test
    void getCompanyJobs_delegatesToJobService() {
        ResultPaginationDTO result = new ResultPaginationDTO();
        when(jobService.fetchJobsByCompany("C-A", PageRequest.of(0, 10))).thenReturn(result);

        RestResponse<ResultPaginationDTO> response = controller.getCompanyJobs("C-A", PageRequest.of(0, 10));

        assertThat(response.getData()).isSameAs(result);
    }

    @Test
    void getAllCompanies_delegatesToCompanyService() {
        ResultPaginationDTO result = new ResultPaginationDTO();
        when(companyService.fetchAll(any(), any())).thenReturn(result);

        RestResponse<ResultPaginationDTO> response = controller.getAllCompanies(null, PageRequest.of(0, 10));

        assertThat(response.getData()).isSameAs(result);
    }
}
