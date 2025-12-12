package org.workfitai.jobservice.controller;

import com.turkraft.springfilter.boot.Filter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.workfitai.jobservice.model.Company;
import org.workfitai.jobservice.model.dto.response.Company.ResCompanyDTO;
import org.workfitai.jobservice.model.dto.response.RestResponse;
import org.workfitai.jobservice.model.dto.response.ResultPaginationDTO;
import org.workfitai.jobservice.service.iCompanyService;
import org.workfitai.jobservice.util.ApiMessage;

import static org.workfitai.jobservice.util.MessageConstant.*;


@RestController
@RequestMapping("/public/companies")
@RequiredArgsConstructor
@Validated
public class CompanyController {

    private final iCompanyService companyService;

    @GetMapping("/{id}")
    @ApiMessage(COMPANY_DETAIL_FETCHED_SUCCESSFULLY)
    public RestResponse<ResCompanyDTO> getCompany(@PathVariable("id") String id) {
        ResCompanyDTO dto = companyService.getById(id);
        return RestResponse.success(dto);
    }

    @GetMapping()
    @ApiMessage(COMPANY_ALL_FETCHED_SUCCESSFULLY)
    public RestResponse<ResultPaginationDTO> getAllCompanies(
            @Filter Specification<Company> spec,
            Pageable pageable) {

        ResultPaginationDTO result = companyService.fetchAll(spec, pageable);
        return RestResponse.success(result);
    }
}
