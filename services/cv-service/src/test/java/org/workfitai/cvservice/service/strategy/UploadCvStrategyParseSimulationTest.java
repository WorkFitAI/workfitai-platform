package org.workfitai.cvservice.service.strategy;

import org.junit.jupiter.api.Test;
import org.workfitai.cvservice.model.dto.ParsedCvData;
import org.workfitai.cvservice.service.nlp.DynamicSkillExtractionService;
import org.workfitai.cvservice.service.nlp.PdfComplexityDetector;
import org.workfitai.cvservice.service.nlp.SemanticMatchingService;
import org.workfitai.cvservice.service.shared.FileService;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Mô phỏng hành vi tách CV thật: SemanticMatchingService chỉ khớp khi gặp
 * đúng cụm tiêu đề section ("experience", "education", "skills", "summary"),
 * còn các dòng ngắn khác (chức danh, ngày tháng, địa điểm...) trả về "ignored"
 * - đúng như embedding model thật sẽ làm với câu ngắn không liên quan.
 */
class UploadCvStrategyParseSimulationTest {

    private ParsedCvData runParse(String cvText) throws Exception {
        SemanticMatchingService semanticMatchingService = mock(SemanticMatchingService.class);
        when(semanticMatchingService.mapToCoreField(anyString())).thenAnswer(invocation -> {
            String header = invocation.getArgument(0, String.class);
            if (header.contains("experience"))
                return "experience";
            if (header.contains("education"))
                return "education";
            if (header.contains("skill"))
                return "skills";
            if (header.contains("summary") || header.contains("objective"))
                return "summary";
            return "ignored";
        });

        DynamicSkillExtractionService skillExtractor = mock(DynamicSkillExtractionService.class);
        when(skillExtractor.extractSkills(anyString())).thenReturn(List.of("java", "spring boot"));

        FileService fileService = mock(FileService.class);

        UploadCvStrategy strategy = new UploadCvStrategy(semanticMatchingService, skillExtractor, fileService,
                new PdfComplexityDetector());

        Method parseCvText = UploadCvStrategy.class.getDeclaredMethod("parseCvText", String.class);
        parseCvText.setAccessible(true);
        return (ParsedCvData) parseCvText.invoke(strategy, cvText);
    }

    @Test
    void parsesRealisticCv_withShortJobTitlesAndDates_shouldNotLoseContent() throws Exception {
        String cvText = """
                Nguyen Van A
                nguyenvana@gmail.com
                0901234567
                github.com/nguyenvana

                Summary
                Backend developer with 3 years of experience building scalable systems.

                Experience
                Senior Backend Engineer
                06/2022 - Present
                Designed and implemented microservices for the payment platform.
                Led a team of 4 engineers to deliver the new checkout flow.

                Backend Engineer
                01/2020 - 05/2022
                Built REST APIs serving over 1 million requests per day.

                Education
                Ho Chi Minh University of Technology
                09/2016 - 06/2020
                Bachelor of Computer Science.

                Skills
                Java, Spring Boot, PostgreSQL, Docker
                """;

        ParsedCvData result = runParse(cvText);

        System.out.println("=== summary ===");
        result.getSummary().forEach(System.out::println);
        System.out.println("=== experience ===");
        result.getExperience().forEach(System.out::println);
        System.out.println("=== education ===");
        result.getEducation().forEach(System.out::println);
        System.out.println("=== skills ===");
        result.getSkills().forEach(System.out::println);

        assertThat(result.getSummary()).isNotEmpty();
        assertThat(result.getExperience())
                .as("các bullet/job title ngắn trong Experience không bị rơi mất")
                .anyMatch(l -> l.contains("Designed and implemented"))
                .anyMatch(l -> l.contains("Led a team"))
                .anyMatch(l -> l.contains("Built REST APIs"));
        assertThat(result.getEducation()).isNotEmpty();
        assertThat(result.getSkills()).contains("java", "spring boot");
    }
}
