package org.workfitai.jobservice.controller.Admin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.workfitai.jobservice.model.dto.request.Skill.ReqCreateSkillDTO;
import org.workfitai.jobservice.model.dto.request.Skill.ReqUpdateSkillDTO;
import org.workfitai.jobservice.model.dto.response.RestResponse;
import org.workfitai.jobservice.model.dto.response.ResultPaginationDTO;
import org.workfitai.jobservice.model.dto.response.Skill.ResSkillDTO;
import org.workfitai.jobservice.model.dto.response.Skill.ResTopSkillDTO;
import org.workfitai.jobservice.model.dto.response.Skill.ResUpdateSkillDTO;
import org.workfitai.jobservice.service.iSkillService;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkillControllerTest {

    @Mock
    private iSkillService skillService;
    @InjectMocks
    private SkillController controller;

    @Test
    void getTopSkills_delegatesToService() {
        List<ResTopSkillDTO> top = List.of(new ResTopSkillDTO(UUID.randomUUID(), "Java", 5));
        when(skillService.getTopSkillsByDemand(20)).thenReturn(top);

        RestResponse<List<ResTopSkillDTO>> response = controller.getTopSkills(20);

        assertThat(response.getData()).isSameAs(top);
    }

    @Test
    void getSkill_delegatesToService() {
        UUID id = UUID.randomUUID();
        ResSkillDTO dto = ResSkillDTO.builder().skillId(id).name("Java").build();
        when(skillService.getById(id)).thenReturn(dto);

        RestResponse<ResSkillDTO> response = controller.getSkill(id);

        assertThat(response.getData()).isSameAs(dto);
    }

    @Test
    void getAllSkills_delegatesToService() {
        ResultPaginationDTO result = new ResultPaginationDTO();
        when(skillService.fetchAll(any(), any())).thenReturn(result);

        RestResponse<ResultPaginationDTO> response = controller.getAllSkills(null, PageRequest.of(0, 10));

        assertThat(response.getData()).isSameAs(result);
    }

    @Test
    void createSkill_delegatesToService() {
        ReqCreateSkillDTO dto = ReqCreateSkillDTO.builder().name("Java").build();
        ResSkillDTO created = ResSkillDTO.builder().name("Java").build();
        when(skillService.create(dto)).thenReturn(created);

        RestResponse<ResSkillDTO> response = controller.createSkill(dto);

        assertThat(response.getData()).isSameAs(created);
    }

    @Test
    void updateSkill_delegatesToService() {
        ReqUpdateSkillDTO dto = ReqUpdateSkillDTO.builder().skillId(UUID.randomUUID()).name("Java 21").build();
        ResUpdateSkillDTO updated = new ResUpdateSkillDTO();
        when(skillService.update(dto)).thenReturn(updated);

        RestResponse<ResUpdateSkillDTO> response = controller.updateSkill(dto);

        assertThat(response.getData()).isSameAs(updated);
    }

    @Test
    void deleteSkill_delegatesToService() {
        UUID id = UUID.randomUUID();

        controller.deleteSkill(id);

        verify(skillService).delete(id);
    }
}
