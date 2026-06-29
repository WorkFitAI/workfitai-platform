package org.workfitai.jobservice.model.mapper;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.workfitai.jobservice.model.Company;
import org.workfitai.jobservice.model.dto.request.Company.ReqCreateCompanyDTO;
import org.workfitai.jobservice.model.dto.request.Company.ReqUpdateCompanyDTO;
import org.workfitai.jobservice.model.dto.response.Company.ResCompanyDTO;

import static org.assertj.core.api.Assertions.assertThat;

class CompanyMapperTest {

    private final CompanyMapper mapper = Mappers.getMapper(CompanyMapper.class);

    @Test
    void toEntity_mapsAllFieldsFromCreateDTO() {
        ReqCreateCompanyDTO dto = ReqCreateCompanyDTO.builder()
                .companyNo("C-A").name("FPT").description("desc").address("Hanoi").size("1000+").build();

        Company entity = mapper.toEntity(dto);

        assertThat(entity.getCompanyNo()).isEqualTo("C-A");
        assertThat(entity.getName()).isEqualTo("FPT");
        assertThat(entity.getDescription()).isEqualTo("desc");
        assertThat(entity.getAddress()).isEqualTo("Hanoi");
        assertThat(entity.getSize()).isEqualTo("1000+");
    }

    @Test
    void updateEntityFromDTO_ignoresNullFields_preservingExistingValues() {
        Company existing = Company.builder().companyNo("C-A").name("FPT").address("Hanoi").size("1000+").build();
        ReqUpdateCompanyDTO dto = ReqUpdateCompanyDTO.builder().companyNo("C-A").name("FPT Software").build();

        mapper.updateEntityFromDTO(dto, existing);

        assertThat(existing.getName()).isEqualTo("FPT Software");
        assertThat(existing.getAddress()).isEqualTo("Hanoi");
        assertThat(existing.getSize()).isEqualTo("1000+");
    }

    @Test
    void toResDTO_mapsEntityToResponse() {
        Company entity = Company.builder().companyNo("C-A").name("FPT").websiteUrl("https://fpt.com").build();

        ResCompanyDTO dto = mapper.toResDTO(entity);

        assertThat(dto.getCompanyNo()).isEqualTo("C-A");
        assertThat(dto.getName()).isEqualTo("FPT");
        assertThat(dto.getWebsiteUrl()).isEqualTo("https://fpt.com");
        assertThat(dto.getAuditId()).isEqualTo("C-A");
    }
}
