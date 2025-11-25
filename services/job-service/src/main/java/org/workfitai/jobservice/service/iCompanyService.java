package org.workfitai.jobservice.service;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.workfitai.jobservice.model.Company;
import org.workfitai.jobservice.model.dto.request.ReqCreateCompanyDTO;
import org.workfitai.jobservice.model.dto.request.ReqUpdateCompanyDTO;
import org.workfitai.jobservice.model.dto.response.ResCompanyDTO;
import org.workfitai.jobservice.model.dto.response.ResUpdateCompanyDTO;
import org.workfitai.jobservice.model.dto.response.ResultPaginationDTO;

public interface iCompanyService {
    ResCompanyDTO getById(String id);

    ResultPaginationDTO fetchAll(Specification<Company> spec, Pageable pageable);

    ResCompanyDTO create(ReqCreateCompanyDTO dto);

    ResUpdateCompanyDTO update(ReqUpdateCompanyDTO dto);

    void delete(String id);
}
