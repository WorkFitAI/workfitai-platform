package org.workfitai.cvservice.service.nlp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class DynamicSkillExtractionServiceTest {

    private DynamicSkillExtractionService service;

    @BeforeEach
    void setUp() {
        service = new DynamicSkillExtractionService();
        service.refreshDictionary();
    }

    @Test
    void extractSkills_returnsMatchedSkills_forKnownText() {
        List<String> result = service.extractSkills("I am a java and spring boot developer");

        assertThat(result).contains("java", "spring boot");
    }

    @Test
    void extractSkills_returnsEmpty_forUnknownText() {
        List<String> result = service.extractSkills("xyz unknown tech 123");

        assertThat(result).isEmpty();
    }

    @Test
    void extractSkills_isCaseInsensitive() {
        List<String> result = service.extractSkills("Experienced JAVA and React developer");

        assertThat(result).contains("java", "react");
    }

    @Test
    void extractSkills_extractsMultiWordSkill() {
        List<String> result = service.extractSkills("next.js and typescript experience");

        assertThat(result).contains("next.js", "typescript");
    }

    @Test
    void extractSkills_extractsAllKnownSkills_inOneSentence() {
        String text = "java spring boot react next.js kafka redis postgresql docker elasticsearch nestjs mongodb typescript";
        List<String> result = service.extractSkills(text);

        assertThat(result).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    void refreshDictionary_rebuildsTrieWithoutError() {
        service.refreshDictionary();

        List<String> result = service.extractSkills("java developer");
        assertThat(result).contains("java");
    }
}
