package org.workfitai.jobservice.controller.HR;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.workfitai.jobservice.model.dto.request.Company.ReqUpdateCompanyDTO;
import org.workfitai.jobservice.model.dto.response.Company.ResUpdateCompanyDTO;
import org.workfitai.jobservice.model.dto.response.RestResponse;
import org.workfitai.jobservice.service.iCompanyService;
import org.workfitai.jobservice.util.ApiMessage;

import static org.workfitai.jobservice.util.MessageConstant.COMPANY_UPDATED_SUCCESSFULLY;

@RestController("hrCompanyController")
@RequestMapping("/hr/companies")
@RequiredArgsConstructor
@Validated
public class CompanyController {

    private final iCompanyService companyService;

    @PreAuthorize("hasAuthority('company:update')")
    @PutMapping(consumes = "multipart/form-data")
    @ApiMessage(COMPANY_UPDATED_SUCCESSFULLY)
    public RestResponse<ResUpdateCompanyDTO> updateCompany(
            @ModelAttribute ReqUpdateCompanyDTO dto) {
        ResUpdateCompanyDTO updated = companyService.update(dto);
        return RestResponse.success(updated);
    }
}
