package org.workfitai.jobservice.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.workfitai.jobservice.config.errors.InvalidDataException;
import org.workfitai.jobservice.config.errors.ResourceConflictException;
import org.workfitai.jobservice.model.Skill;
import org.workfitai.jobservice.model.dto.request.Skill.ReqCreateSkillDTO;
import org.workfitai.jobservice.model.dto.request.Skill.ReqUpdateSkillDTO;
import org.workfitai.jobservice.model.dto.response.Skill.ResSkillDTO;
import org.workfitai.jobservice.model.dto.response.Skill.ResUpdateSkillDTO;
import org.workfitai.jobservice.model.enums.JobStatus;
import org.workfitai.jobservice.model.mapper.SkillMapper;
import org.workfitai.jobservice.repository.SkillDemandProjection;
import org.workfitai.jobservice.repository.SkillRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkillServiceTest {

    @Mock
    private SkillRepository skillRepository;
    @Mock
    private SkillMapper skillMapper;
    @InjectMocks
    private SkillService skillService;

    private Skill skill(UUID id, String name) {
        Skill skill = new Skill(name);
        skill.setSkillId(id);
        return skill;
    }

    @Test
    void getById_returnsMappedDTO_whenFound() {
        UUID id = UUID.randomUUID();
        Skill skill = skill(id, "Java");
        when(skillRepository.findById(id)).thenReturn(Optional.of(skill));
        ResSkillDTO dto = new ResSkillDTO();
        when(skillMapper.toResDTO(skill)).thenReturn(dto);

        assertThat(skillService.getById(id)).isSameAs(dto);
    }

    @Test
    void getById_throwsInvalidData_whenNotFound() {
        UUID id = UUID.randomUUID();
        when(skillRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> skillService.getById(id))
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("Skill not found");
    }

    @Test
    void create_savesNewSkill_whenNameIsUnique() {
        ReqCreateSkillDTO dto = ReqCreateSkillDTO.builder().name("Java").build();
        when(skillRepository.findByName("Java")).thenReturn(Optional.empty());
        Skill entity = skill(UUID.randomUUID(), "Java");
        when(skillMapper.toEntity(dto)).thenReturn(entity);
        ResSkillDTO resDto = new ResSkillDTO();
        when(skillMapper.toResDTO(entity)).thenReturn(resDto);

        ResSkillDTO result = skillService.create(dto);

        verify(skillRepository).save(entity);
        assertThat(result).isSameAs(resDto);
    }

    @Test
    void create_throwsConflict_whenNameAlreadyExists() {
        ReqCreateSkillDTO dto = ReqCreateSkillDTO.builder().name("Java").build();
        when(skillRepository.findByName("Java")).thenReturn(Optional.of(skill(UUID.randomUUID(), "Java")));

        assertThatThrownBy(() -> skillService.create(dto))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessage("Skill with the same name already exists");
    }

    @Test
    void update_succeeds_whenNoNameConflict() {
        UUID id = UUID.randomUUID();
        Skill existing = skill(id, "Java");
        ReqUpdateSkillDTO dto = ReqUpdateSkillDTO.builder().skillId(id).name("Java 21").build();
        when(skillRepository.findById(id)).thenReturn(Optional.of(existing));
        when(skillRepository.findByName("Java 21")).thenReturn(Optional.empty());
        ResUpdateSkillDTO resDto = new ResUpdateSkillDTO();
        when(skillMapper.toResUpdateDTO(existing)).thenReturn(resDto);

        ResUpdateSkillDTO result = skillService.update(dto);

        verify(skillMapper).updateEntityFromDTO(dto, existing);
        verify(skillRepository).save(existing);
        assertThat(result).isSameAs(resDto);
    }

    @Test
    void update_allowsRenamingToOwnCurrentName() {
        UUID id = UUID.randomUUID();
        Skill existing = skill(id, "Java");
        ReqUpdateSkillDTO dto = ReqUpdateSkillDTO.builder().skillId(id).name("Java").build();
        when(skillRepository.findById(id)).thenReturn(Optional.of(existing));
        when(skillRepository.findByName("Java")).thenReturn(Optional.of(existing));
        when(skillMapper.toResUpdateDTO(existing)).thenReturn(new ResUpdateSkillDTO());

        skillService.update(dto);

        verify(skillRepository).save(existing);
    }

    @Test
    void update_throwsConflict_whenNameBelongsToAnotherSkill() {
        UUID id = UUID.randomUUID();
        Skill existing = skill(id, "Java");
        Skill other = skill(UUID.randomUUID(), "Python");
        ReqUpdateSkillDTO dto = ReqUpdateSkillDTO.builder().skillId(id).name("Python").build();
        when(skillRepository.findById(id)).thenReturn(Optional.of(existing));
        when(skillRepository.findByName("Python")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> skillService.update(dto))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessage("Skill with the same name already exists");
    }

    @Test
    void update_throwsInvalidData_whenSkillNotFound() {
        UUID id = UUID.randomUUID();
        when(skillRepository.findById(id)).thenReturn(Optional.empty());
        ReqUpdateSkillDTO dto = ReqUpdateSkillDTO.builder().skillId(id).name("Java").build();

        assertThatThrownBy(() -> skillService.update(dto))
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("Skill not found");
    }

    @Test
    void delete_removesSkill_whenExists() {
        UUID id = UUID.randomUUID();
        when(skillRepository.existsById(id)).thenReturn(true);

        skillService.delete(id);

        verify(skillRepository).deleteById(id);
    }

    @Test
    void delete_throwsInvalidData_whenNotExists() {
        UUID id = UUID.randomUUID();
        when(skillRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> skillService.delete(id))
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("Skill not found");
        verify(skillRepository, never()).deleteById(any());
    }

    @Test
    void getTopSkillsByDemand_mapsProjectionsToDTO() {
        UUID id = UUID.randomUUID();
        SkillDemandProjection projection = mock(SkillDemandProjection.class);
        when(projection.getSkillId()).thenReturn(id);
        when(projection.getSkillName()).thenReturn("Java");
        when(projection.getJobCount()).thenReturn(5L);
        when(skillRepository.findTopSkillsByPublishedJobCount(eq(JobStatus.PUBLISHED), any(PageRequest.class)))
                .thenReturn(List.of(projection));

        var result = skillService.getTopSkillsByDemand(3);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).skillId()).isEqualTo(id);
        assertThat(result.get(0).skillName()).isEqualTo("Java");
        assertThat(result.get(0).jobCount()).isEqualTo(5L);
    }
}
