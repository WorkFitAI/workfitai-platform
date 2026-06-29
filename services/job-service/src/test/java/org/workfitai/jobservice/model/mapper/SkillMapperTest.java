package org.workfitai.jobservice.model.mapper;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.workfitai.jobservice.model.Skill;
import org.workfitai.jobservice.model.dto.request.Skill.ReqCreateSkillDTO;
import org.workfitai.jobservice.model.dto.request.Skill.ReqUpdateSkillDTO;
import org.workfitai.jobservice.model.dto.response.Skill.ResSkillDTO;
import org.workfitai.jobservice.model.dto.response.Skill.ResUpdateSkillDTO;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SkillMapperTest {

    private final SkillMapper mapper = Mappers.getMapper(SkillMapper.class);

    @Test
    void toEntity_mapsNameFromCreateDTO() {
        ReqCreateSkillDTO dto = ReqCreateSkillDTO.builder().name("Java").build();

        Skill entity = mapper.toEntity(dto);

        assertThat(entity.getName()).isEqualTo("Java");
    }

    @Test
    void updateEntityFromDTO_overwritesName() {
        Skill skill = new Skill("Java");
        UUID id = UUID.randomUUID();
        skill.setSkillId(id);
        ReqUpdateSkillDTO dto = ReqUpdateSkillDTO.builder().skillId(id).name("Java 21").build();

        mapper.updateEntityFromDTO(dto, skill);

        assertThat(skill.getName()).isEqualTo("Java 21");
        assertThat(skill.getSkillId()).isEqualTo(id);
    }

    @Test
    void toResDTO_mapsIdAndName() {
        UUID id = UUID.randomUUID();
        Skill skill = new Skill("Java");
        skill.setSkillId(id);

        ResSkillDTO dto = mapper.toResDTO(skill);

        assertThat(dto.getSkillId()).isEqualTo(id);
        assertThat(dto.getName()).isEqualTo("Java");
        assertThat(dto.getAuditId()).isEqualTo(id.toString());
    }

    @Test
    void toResUpdateDTO_mapsIdAndName() {
        UUID id = UUID.randomUUID();
        Skill skill = new Skill("Python");
        skill.setSkillId(id);

        ResUpdateSkillDTO dto = mapper.toResUpdateDTO(skill);

        assertThat(dto.getSkillId()).isEqualTo(id);
        assertThat(dto.getName()).isEqualTo("Python");
    }
}
