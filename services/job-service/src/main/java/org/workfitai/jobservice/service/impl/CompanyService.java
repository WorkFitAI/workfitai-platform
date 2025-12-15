package org.workfitai.jobservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.workfitai.jobservice.model.Company;
import org.workfitai.jobservice.model.dto.request.Company.ReqCreateCompanyDTO;
import org.workfitai.jobservice.model.dto.request.Company.ReqUpdateCompanyDTO;
import org.workfitai.jobservice.model.dto.response.Company.ResCompanyDTO;
import org.workfitai.jobservice.model.dto.response.Company.ResUpdateCompanyDTO;
import org.workfitai.jobservice.model.dto.response.ResultPaginationDTO;
import org.workfitai.jobservice.model.mapper.CompanyMapper;
import org.workfitai.jobservice.repository.CompanyRepository;
import org.workfitai.jobservice.service.CloudinaryService;
import org.workfitai.jobservice.service.iCompanyService;
import org.workfitai.jobservice.util.PaginationUtils;

import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyService implements iCompanyService {
    private final CompanyRepository companyRepository;
    private final CompanyMapper companyMapper;
    private final CloudinaryService cloudinaryService;

    @Override
    public ResCompanyDTO getById(String id) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Company not found"));
        return companyMapper.toResDTO(company);
    }

    @Override
    public ResultPaginationDTO fetchAll(Specification<Company> spec, Pageable pageable) {
        Page<Company> pageCompany = companyRepository.findAll(spec, pageable);
        return PaginationUtils.toResultPaginationDTO(pageCompany, companyMapper::toResDTO);
    }

    @Override
    public ResCompanyDTO create(ReqCreateCompanyDTO dto) {
        Company company = companyMapper.toEntity(dto);
        companyRepository.save(company);
        return companyMapper.toResDTO(company);
    }

    @Override
    public ResUpdateCompanyDTO update(ReqUpdateCompanyDTO dto) {
        Company company = companyRepository.findById(dto.getCompanyNo())
                .orElseThrow(() -> new RuntimeException("Company not found"));

        if (dto.getLogoFile() != null && !dto.getLogoFile().isEmpty()) {
            try {
                String logoUrl = cloudinaryService.uploadFile(dto.getLogoFile(), dto.getCompanyNo());
                company.setLogoUrl(logoUrl);
            } catch (IOException e) {
                throw new RuntimeException("Failed to upload logo", e);
            }
        }

        companyMapper.updateEntityFromDTO(dto, company);

        companyRepository.save(company);

        return companyMapper.toResUpdateDTO(company);
    }


    @Override
    public void delete(String id) {
        if (!companyRepository.existsById(id)) {
            throw new RuntimeException("Company not found");
        }
        companyRepository.deleteById(id);
    }
}
