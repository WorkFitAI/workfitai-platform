package org.workfitai.jobservice.controller.HR;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workfitai.jobservice.model.dto.request.Company.ReqUpdateCompanyDTO;
import org.workfitai.jobservice.model.dto.response.Company.ResUpdateCompanyDTO;
import org.workfitai.jobservice.model.dto.response.RestResponse;
import org.workfitai.jobservice.service.iCompanyService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyControllerTest {

    @Mock
    private iCompanyService companyService;
    @InjectMocks
    private CompanyController controller;

    @Test
    void updateCompany_delegatesToService() {
        ReqUpdateCompanyDTO dto = ReqUpdateCompanyDTO.builder().companyNo("C-A").build();
        ResUpdateCompanyDTO updated = new ResUpdateCompanyDTO();
        when(companyService.update(dto)).thenReturn(updated);

        RestResponse<ResUpdateCompanyDTO> response = controller.updateCompany(dto);

        assertThat(response.getData()).isSameAs(updated);
    }
}
