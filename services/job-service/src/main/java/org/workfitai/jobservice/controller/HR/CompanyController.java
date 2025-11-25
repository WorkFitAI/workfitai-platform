package org.workfitai.jobservice.controller;

import com.turkraft.springfilter.boot.Filter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.workfitai.jobservice.model.Company;
import org.workfitai.jobservice.model.dto.request.ReqCreateCompanyDTO;
import org.workfitai.jobservice.model.dto.request.ReqUpdateCompanyDTO;
import org.workfitai.jobservice.model.dto.response.ResCompanyDTO;
import org.workfitai.jobservice.model.dto.response.ResUpdateCompanyDTO;
import org.workfitai.jobservice.model.dto.response.RestResponse;
import org.workfitai.jobservice.model.dto.response.ResultPaginationDTO;
import org.workfitai.jobservice.service.iCompanyService;
import org.workfitai.jobservice.util.ApiMessage;

import static org.workfitai.jobservice.util.MessageConstant.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping()
@RequiredArgsConstructor
@Validated
public class CompanyController {

    private final iCompanyService companyService;

    @GetMapping("/public/companies/{id}")
    @ApiMessage(COMPANY_DETAIL_FETCHED_SUCCESSFULLY)
    public RestResponse<ResCompanyDTO> getCompany(@PathVariable("id") String id) {
        ResCompanyDTO dto = companyService.getById(id);
        return RestResponse.success(dto);
    }

    @GetMapping("/public/companies")
    @ApiMessage(COMPANY_ALL_FETCHED_SUCCESSFULLY)
    public RestResponse<ResultPaginationDTO> getAllCompanies(
            @Filter Specification<Company> spec,
            Pageable pageable) {

        ResultPaginationDTO result = companyService.fetchAll(spec, pageable);
        return RestResponse.success(result);
    }

    @PostMapping("/public/companies")
    @ApiMessage(COMPANY_CREATED_SUCCESSFULLY)
    public RestResponse<ResCompanyDTO> createCompany(
            @Valid @RequestBody ReqCreateCompanyDTO dto) {
        ResCompanyDTO created = companyService.create(dto);
        return RestResponse.success(created);
    }

    @PutMapping("/public/companies")
    @ApiMessage(COMPANY_UPDATED_SUCCESSFULLY)
    public RestResponse<ResUpdateCompanyDTO> updateCompany(
            @Valid @RequestBody ReqUpdateCompanyDTO dto) {
        ResUpdateCompanyDTO updated = companyService.update(dto);
        return RestResponse.success(updated);
    }

    @DeleteMapping("/public/companies")
    @ApiMessage(COMPANY_DELETED_SUCCESSFULLY)
    public RestResponse<Void> deleteCompany(@PathVariable("id") String id) {
        companyService.delete(id);
        return RestResponse.success(null);
    }
}
