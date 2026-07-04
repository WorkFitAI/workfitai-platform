package org.workfitai.cvservice.service.nlp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workfitai.cvservice.client.JobServiceSkillClient;
import org.workfitai.cvservice.client.dto.JobServiceApiResponseDTO;
import org.workfitai.cvservice.client.dto.SkillDTO;
import org.workfitai.cvservice.client.dto.SkillPageResultDTO;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamicSkillExtractionServiceTest {

    private static final List<String> KNOWN_SKILLS = List.of(
            "java", "spring boot", "react", "next.js", "kafka", "redis",
            "postgresql", "docker", "elasticsearch", "nestjs", "mongodb", "typescript");

    @Mock
    private JobServiceSkillClient jobServiceSkillClient;

    private DynamicSkillExtractionService service;

    @BeforeEach
    void setUp() {
        service = new DynamicSkillExtractionService(jobServiceSkillClient);
    }

    private static JobServiceApiResponseDTO<SkillPageResultDTO> pageResponse(List<String> names, int page,
            int totalPages) {
        SkillPageResultDTO.Meta meta = new SkillPageResultDTO.Meta();
        meta.setPage(page);
        meta.setPageSize(names.size());
        meta.setPages(totalPages);
        meta.setTotal(names.size());

        List<SkillDTO> skills = names.stream().map(name -> {
            SkillDTO dto = new SkillDTO();
            dto.setName(name);
            return dto;
        }).toList();

        SkillPageResultDTO result = new SkillPageResultDTO();
        result.setMeta(meta);
        result.setResult(skills);

        JobServiceApiResponseDTO<SkillPageResultDTO> response = new JobServiceApiResponseDTO<>();
        response.setStatus(200);
        response.setData(result);
        return response;
    }

    @Test
    void extractSkills_returnsMatchedSkills_forKnownText() {
        when(jobServiceSkillClient.getAllSkills(anyInt(), anyInt())).thenReturn(pageResponse(KNOWN_SKILLS, 0, 1));
        service.refreshDictionary();

        List<String> result = service.extractSkills("I am a java and spring boot developer");

        assertThat(result).contains("java", "spring boot");
    }

    @Test
    void extractSkills_returnsEmpty_forUnknownText() {
        when(jobServiceSkillClient.getAllSkills(anyInt(), anyInt())).thenReturn(pageResponse(KNOWN_SKILLS, 0, 1));
        service.refreshDictionary();

        List<String> result = service.extractSkills("xyz unknown tech 123");

        assertThat(result).isEmpty();
    }

    @Test
    void extractSkills_isCaseInsensitive() {
        when(jobServiceSkillClient.getAllSkills(anyInt(), anyInt())).thenReturn(pageResponse(KNOWN_SKILLS, 0, 1));
        service.refreshDictionary();

        List<String> result = service.extractSkills("Experienced JAVA and React developer");

        assertThat(result).contains("java", "react");
    }

    @Test
    void extractSkills_extractsMultiWordSkill() {
        when(jobServiceSkillClient.getAllSkills(anyInt(), anyInt())).thenReturn(pageResponse(KNOWN_SKILLS, 0, 1));
        service.refreshDictionary();

        List<String> result = service.extractSkills("next.js and typescript experience");

        assertThat(result).contains("next.js", "typescript");
    }

    @Test
    void extractSkills_extractsAllKnownSkills_inOneSentence() {
        when(jobServiceSkillClient.getAllSkills(anyInt(), anyInt())).thenReturn(pageResponse(KNOWN_SKILLS, 0, 1));
        service.refreshDictionary();

        String text = "java spring boot react next.js kafka redis postgresql docker elasticsearch nestjs mongodb typescript";
        List<String> result = service.extractSkills(text);

        assertThat(result).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    void refreshDictionary_rebuildsTrieWithoutError() {
        when(jobServiceSkillClient.getAllSkills(anyInt(), anyInt())).thenReturn(pageResponse(KNOWN_SKILLS, 0, 1));
        service.refreshDictionary();
        service.refreshDictionary();

        List<String> result = service.extractSkills("java developer");
        assertThat(result).contains("java");
    }

    @Test
    void refreshDictionary_accumulatesSkillsAcrossPages() {
        when(jobServiceSkillClient.getAllSkills(0, 200))
                .thenReturn(pageResponse(List.of("java", "react"), 0, 2));
        when(jobServiceSkillClient.getAllSkills(1, 200))
                .thenReturn(pageResponse(List.of("kafka"), 1, 2));

        service.refreshDictionary();

        List<String> result = service.extractSkills("java react kafka");
        assertThat(result).containsExactlyInAnyOrder("java", "react", "kafka");
    }

    @Test
    void refreshDictionary_fallsBackToSeedList_whenJobServiceUnreachableOnFirstLoad() {
        when(jobServiceSkillClient.getAllSkills(anyInt(), anyInt()))
                .thenThrow(new RuntimeException("connection refused"));

        service.refreshDictionary();

        List<String> result = service.extractSkills("java developer with docker skills");
        assertThat(result).contains("java", "docker");
    }

    @Test
    void refreshDictionary_keepsLastGoodDictionary_whenJobServiceFailsAfterSuccessfulLoad() {
        when(jobServiceSkillClient.getAllSkills(anyInt(), anyInt()))
                .thenReturn(pageResponse(List.of("java", "kubernetes"), 0, 1))
                .thenThrow(new RuntimeException("timeout"));

        service.refreshDictionary();
        service.refreshDictionary();

        List<String> result = service.extractSkills("java and kubernetes expert");
        assertThat(result).containsExactlyInAnyOrder("java", "kubernetes");
    }
}
