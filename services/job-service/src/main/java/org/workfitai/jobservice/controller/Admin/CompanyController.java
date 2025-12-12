package org.workfitai.jobservice.controller.Admin;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.workfitai.jobservice.model.dto.request.Company.ReqCreateCompanyDTO;
import org.workfitai.jobservice.model.dto.request.Company.ReqUpdateCompanyDTO;
import org.workfitai.jobservice.model.dto.response.Company.ResCompanyDTO;
import org.workfitai.jobservice.model.dto.response.Company.ResUpdateCompanyDTO;
import org.workfitai.jobservice.model.dto.response.RestResponse;
import org.workfitai.jobservice.service.iCompanyService;
import org.workfitai.jobservice.util.ApiMessage;

import static org.workfitai.jobservice.util.MessageConstant.*;

@RestController("adminCompanyController")
@RequestMapping("/admin/companies")
@RequiredArgsConstructor
@Validated
public class CompanyController {

    private final iCompanyService companyService;

    @PostMapping()
    @ApiMessage(COMPANY_CREATED_SUCCESSFULLY)
    public RestResponse<ResCompanyDTO> createCompany(
            @Valid @RequestBody ReqCreateCompanyDTO dto) {
        ResCompanyDTO created = companyService.create(dto);
        return RestResponse.success(created);
    }

    @PutMapping()
    @ApiMessage(COMPANY_UPDATED_SUCCESSFULLY)
    public RestResponse<ResUpdateCompanyDTO> updateCompany(
            @Valid @RequestBody ReqUpdateCompanyDTO dto) {
        ResUpdateCompanyDTO updated = companyService.update(dto);
        return RestResponse.success(updated);
    }

    @DeleteMapping()
    @ApiMessage(COMPANY_DELETED_SUCCESSFULLY)
    public RestResponse<Void> deleteCompany(@PathVariable("id") String id) {
        companyService.delete(id);
        return RestResponse.success(null);
    }
}
