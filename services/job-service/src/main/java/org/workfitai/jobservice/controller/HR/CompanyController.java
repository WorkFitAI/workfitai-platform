package org.workfitai.jobservice.controller.HR;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.workfitai.jobservice.model.dto.request.ReqUpdateCompanyDTO;
import org.workfitai.jobservice.model.dto.response.ResUpdateCompanyDTO;
import org.workfitai.jobservice.model.dto.response.RestResponse;
import org.workfitai.jobservice.service.iCompanyService;
import org.workfitai.jobservice.util.ApiMessage;

import static org.workfitai.jobservice.util.MessageConstant.*;

@RestController("hrCompanyController")
@RequestMapping("/hr/companies")
@RequiredArgsConstructor
@Validated
public class CompanyController {

    private final iCompanyService companyService;

    @PutMapping()
    @ApiMessage(COMPANY_UPDATED_SUCCESSFULLY)
    public RestResponse<ResUpdateCompanyDTO> updateCompany(
            @Valid @RequestBody ReqUpdateCompanyDTO dto) {
        ResUpdateCompanyDTO updated = companyService.update(dto);
        return RestResponse.success(updated);
    }
}
