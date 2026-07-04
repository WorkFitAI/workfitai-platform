package org.workfitai.cvservice.service.strategy;

import org.junit.jupiter.api.Test;
import org.workfitai.cvservice.model.CV;
import org.workfitai.cvservice.model.dto.request.ReqCvTemplateDTO;
import org.workfitai.cvservice.model.enums.TemplateType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateCvStrategyTest {

    private final TemplateCvStrategy strategy = new TemplateCvStrategy();

    @Test
    void createCv_mapsTemplateDtoFieldsOntoEntity() {
        ReqCvTemplateDTO dto = new ReqCvTemplateDTO();
        dto.setHeadline("Backend Engineer");
        dto.setSummary("Experienced backend engineer");
        dto.setTemplateType(TemplateType.TECH);
        dto.setSections(Map.of("skills", "Java"));

        CV cv = strategy.createCv(dto);

        assertThat(cv.getHeadline()).isEqualTo("Backend Engineer");
        assertThat(cv.getSummary()).isEqualTo("Experienced backend engineer");
        assertThat(cv.getTemplateType()).isEqualTo(TemplateType.TECH);
        assertThat(cv.getSections()).containsEntry("skills", "Java");
    }
}
