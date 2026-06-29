package org.workfitai.jobservice.controller.Admin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workfitai.jobservice.model.dto.request.Company.ReqCreateCompanyDTO;
import org.workfitai.jobservice.model.dto.request.Company.ReqUpdateCompanyDTO;
import org.workfitai.jobservice.model.dto.response.Company.ResCompanyDTO;
import org.workfitai.jobservice.model.dto.response.Company.ResUpdateCompanyDTO;
import org.workfitai.jobservice.model.dto.response.RestResponse;
import org.workfitai.jobservice.service.iCompanyService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyControllerTest {

    @Mock
    private iCompanyService companyService;
    @InjectMocks
    private CompanyController controller;

    @Test
    void createCompany_delegatesToService() {
        ReqCreateCompanyDTO dto = ReqCreateCompanyDTO.builder().companyNo("C-A").name("FPT").build();
        ResCompanyDTO created = ResCompanyDTO.builder().companyNo("C-A").build();
        when(companyService.create(dto)).thenReturn(created);

        RestResponse<ResCompanyDTO> response = controller.createCompany(dto);

        assertThat(response.getData()).isSameAs(created);
    }

    @Test
    void updateCompany_delegatesToService() {
        ReqUpdateCompanyDTO dto = ReqUpdateCompanyDTO.builder().companyNo("C-A").build();
        ResUpdateCompanyDTO updated = new ResUpdateCompanyDTO();
        when(companyService.update(dto)).thenReturn(updated);

        RestResponse<ResUpdateCompanyDTO> response = controller.updateCompany(dto);

        assertThat(response.getData()).isSameAs(updated);
    }

    @Test
    void deleteCompany_delegatesToService() {
        controller.deleteCompany("C-A");

        verify(companyService).delete("C-A");
    }
}
