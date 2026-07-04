package org.workfitai.jobservice.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.workfitai.jobservice.model.JobCategory;
import org.workfitai.jobservice.model.dto.request.JobCategory.ReqCreateJobCategoryDTO;
import org.workfitai.jobservice.model.dto.request.JobCategory.ReqUpdateJobCategoryDTO;
import org.workfitai.jobservice.model.dto.response.JobCategory.ResJobCategoryDTO;
import org.workfitai.jobservice.model.dto.response.ResultPaginationDTO;
import org.workfitai.jobservice.model.mapper.JobCategoryMapper;
import org.workfitai.jobservice.repository.JobCategoryRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobCategoryServiceTest {

    @Mock
    private JobCategoryRepository jobCategoryRepository;
    @Mock
    private JobCategoryMapper jobCategoryMapper;
    @InjectMocks
    private JobCategoryService jobCategoryService;

    @Test
    void getById_returnsMappedDTO_whenFound() {
        UUID id = UUID.randomUUID();
        JobCategory category = JobCategory.builder().jobCategoryId(id).name("Engineering").build();
        when(jobCategoryRepository.findById(id)).thenReturn(Optional.of(category));
        ResJobCategoryDTO dto = new ResJobCategoryDTO();
        when(jobCategoryMapper.toResDTO(category)).thenReturn(dto);

        assertThat(jobCategoryService.getById(id)).isSameAs(dto);
    }

    @Test
    void getById_throws_whenNotFound() {
        UUID id = UUID.randomUUID();
        when(jobCategoryRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobCategoryService.getById(id))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Job category not found");
    }

    @Test
    void fetchAll_returnsPaginatedDTO() {
        JobCategory category = JobCategory.builder().jobCategoryId(UUID.randomUUID()).name("Engineering").build();
        Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        Specification<JobCategory> spec = (root, query, cb) -> null;
        Page<JobCategory> page = new PageImpl<>(List.of(category), pageable, 1);
        when(jobCategoryRepository.findAll(spec, pageable)).thenReturn(page);
        ResJobCategoryDTO resDto = new ResJobCategoryDTO();
        when(jobCategoryMapper.toResDTO(category)).thenReturn(resDto);

        ResultPaginationDTO result = jobCategoryService.fetchAll(spec, pageable);

        assertThat(result).isNotNull();
        assertThat(result.getResult()).isEqualTo(List.of(resDto));
    }

    @Test
    void create_savesEntityAndReturnsDTO() {
        ReqCreateJobCategoryDTO dto = new ReqCreateJobCategoryDTO();
        dto.setName("Engineering");
        JobCategory entity = JobCategory.builder().name("Engineering").build();
        JobCategory saved = JobCategory.builder().jobCategoryId(UUID.randomUUID()).name("Engineering").build();
        when(jobCategoryMapper.toEntity(dto)).thenReturn(entity);
        when(jobCategoryRepository.save(entity)).thenReturn(saved);
        ResJobCategoryDTO resDto = new ResJobCategoryDTO();
        when(jobCategoryMapper.toResDTO(saved)).thenReturn(resDto);

        assertThat(jobCategoryService.create(dto)).isSameAs(resDto);
    }

    @Test
    void update_succeeds_whenFound() {
        UUID id = UUID.randomUUID();
        JobCategory category = JobCategory.builder().jobCategoryId(id).name("Old").build();
        ReqUpdateJobCategoryDTO dto = new ReqUpdateJobCategoryDTO();
        dto.setId(id);
        dto.setName("New");
        when(jobCategoryRepository.findById(id)).thenReturn(Optional.of(category));
        when(jobCategoryRepository.save(category)).thenReturn(category);
        ResJobCategoryDTO resDto = new ResJobCategoryDTO();
        when(jobCategoryMapper.toResDTO(category)).thenReturn(resDto);

        assertThat(jobCategoryService.update(dto)).isSameAs(resDto);
        verify(jobCategoryMapper).updateEntityFromDTO(dto, category);
    }

    @Test
    void update_throws_whenNotFound() {
        UUID id = UUID.randomUUID();
        when(jobCategoryRepository.findById(id)).thenReturn(Optional.empty());
        ReqUpdateJobCategoryDTO dto = new ReqUpdateJobCategoryDTO();
        dto.setId(id);

        assertThatThrownBy(() -> jobCategoryService.update(dto))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Job category not found");
    }

    @Test
    void delete_removesCategory_whenExists() {
        UUID id = UUID.randomUUID();
        when(jobCategoryRepository.existsById(id)).thenReturn(true);

        jobCategoryService.delete(id);

        verify(jobCategoryRepository).deleteById(id);
    }

    @Test
    void delete_throws_whenNotExists() {
        UUID id = UUID.randomUUID();
        when(jobCategoryRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> jobCategoryService.delete(id))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Job category not found");
        verify(jobCategoryRepository, never()).deleteById(any());
    }
}
