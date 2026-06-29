package org.workfitai.cvservice.model.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.workfitai.cvservice.model.CV;
import org.workfitai.cvservice.model.dto.response.ResCvDTO;
import org.workfitai.cvservice.model.enums.TemplateType;

class CVMapperTest {

    @Test
    void toResDTO_mapsApplicationId_forSnapshotCv() {
        CV cv = new CV();
        cv.setCvId("cv-1");
        cv.setBelongTo("vanphat15it");
        cv.setTemplateType(TemplateType.TECH);
        cv.setApplicationId("app-123");

        ResCvDTO res = CVMapper.INSTANCE.toResDTO(cv);

        assertThat(res.getApplicationId()).isEqualTo("app-123");
    }

    @Test
    void toResDTO_leavesApplicationIdNull_forRegularCv() {
        CV cv = new CV();
        cv.setCvId("cv-2");
        cv.setBelongTo("vanphat15it");
        cv.setTemplateType(TemplateType.TECH);

        ResCvDTO res = CVMapper.INSTANCE.toResDTO(cv);

        assertThat(res.getApplicationId()).isNull();
    }
}
