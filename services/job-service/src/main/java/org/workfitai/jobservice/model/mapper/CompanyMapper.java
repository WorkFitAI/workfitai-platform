package org.workfitai.jobservice.model.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.workfitai.jobservice.model.Company;
import org.workfitai.jobservice.model.dto.request.Company.ReqCreateCompanyDTO;
import org.workfitai.jobservice.model.dto.request.Company.ReqUpdateCompanyDTO;
import org.workfitai.jobservice.model.dto.response.Company.ResCompanyDTO;
import org.workfitai.jobservice.model.dto.response.Company.ResUpdateCompanyDTO;

@Mapper(componentModel = "spring")
public interface CompanyMapper {

    Company toEntity(ReqCreateCompanyDTO dto);

    /**
     * Map DTO -> Entity nhưng không override createdAt, createdBy
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromDTO(ReqUpdateCompanyDTO dto, @MappingTarget Company company);

    ResCompanyDTO toResDTO(Company company);

    ResUpdateCompanyDTO toResUpdateDTO(Company company);
}
