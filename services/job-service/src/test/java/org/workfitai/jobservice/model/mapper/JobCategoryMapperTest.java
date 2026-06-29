package org.workfitai.jobservice.model.mapper;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.workfitai.jobservice.model.JobCategory;
import org.workfitai.jobservice.model.dto.request.JobCategory.ReqCreateJobCategoryDTO;
import org.workfitai.jobservice.model.dto.request.JobCategory.ReqUpdateJobCategoryDTO;
import org.workfitai.jobservice.model.dto.response.JobCategory.ResJobCategoryDTO;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JobCategoryMapperTest {

    private final JobCategoryMapper mapper = Mappers.getMapper(JobCategoryMapper.class);

    @Test
    void toEntity_mapsNameFromCreateDTO() {
        ReqCreateJobCategoryDTO dto = new ReqCreateJobCategoryDTO();
        dto.setName("Engineering");

        JobCategory entity = mapper.toEntity(dto);

        assertThat(entity.getName()).isEqualTo("Engineering");
    }

    @Test
    void toResDTO_mapsIdAndName() {
        UUID id = UUID.randomUUID();
        JobCategory entity = JobCategory.builder().jobCategoryId(id).name("Engineering").build();

        ResJobCategoryDTO dto = mapper.toResDTO(entity);

        assertThat(dto.getId()).isEqualTo(id);
        assertThat(dto.getName()).isEqualTo("Engineering");
    }

    @Test
    void updateEntityFromDTO_overwritesName() {
        UUID id = UUID.randomUUID();
        JobCategory entity = JobCategory.builder().jobCategoryId(id).name("Old").build();
        ReqUpdateJobCategoryDTO dto = new ReqUpdateJobCategoryDTO();
        dto.setId(id);
        dto.setName("New");

        mapper.updateEntityFromDTO(dto, entity);

        assertThat(entity.getName()).isEqualTo("New");
        assertThat(entity.getJobCategoryId()).isEqualTo(id);
    }
}
