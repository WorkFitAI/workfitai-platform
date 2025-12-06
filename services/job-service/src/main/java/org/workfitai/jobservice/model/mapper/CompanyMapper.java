package org.workfitai.jobservice.model.mapper;

import org.mapstruct.*;
import org.mapstruct.factory.Mappers;
import org.workfitai.jobservice.model.Company;
import org.workfitai.jobservice.model.dto.request.ReqCreateCompanyDTO;
import org.workfitai.jobservice.model.dto.request.ReqUpdateCompanyDTO;
import org.workfitai.jobservice.model.dto.response.ResCompanyDTO;
import org.workfitai.jobservice.model.dto.response.ResUpdateCompanyDTO;

@Mapper(componentModel = "spring")
public interface CompanyMapper {

    Company toEntity(ReqCreateCompanyDTO dto);

    /**
     * Map DTO -> Entity nhưng không override createdAt, createdBy
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromDTO(ReqUpdateCompanyDTO dto, @MappingTarget Company company);

    ResCompanyDTO toResDTO(Company company);

    @Mapping(target = "updatedAt", expression = "java(company.getLastModifiedDate() != null ? company.getLastModifiedDate().toString() : null)")
    ResUpdateCompanyDTO toResUpdateDTO(Company company);
}
