package org.workfitai.cvservice.service.factory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workfitai.cvservice.service.strategy.CvCreationStrategy;
import org.workfitai.cvservice.service.strategy.TemplateCvStrategy;
import org.workfitai.cvservice.service.strategy.UploadCvStrategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class CvCreationFactoryTest {

    @Mock
    private TemplateCvStrategy templateCvStrategy;
    @Mock
    private UploadCvStrategy uploadCvStrategy;

    private CvCreationFactory factory;

    private void init() {
        factory = new CvCreationFactory(templateCvStrategy, uploadCvStrategy);
    }

    @Test
    void getStrategy_returnsTemplateStrategy_forTemplateType() {
        init();

        CvCreationStrategy<?> strategy = factory.getStrategy("template");

        assertThat(strategy).isSameAs(templateCvStrategy);
    }

    @Test
    void getStrategy_returnsUploadStrategy_forUploadType() {
        init();

        CvCreationStrategy<?> strategy = factory.getStrategy("upload");

        assertThat(strategy).isSameAs(uploadCvStrategy);
    }

    @Test
    void getStrategy_throwsIllegalArgumentException_forUnknownType() {
        init();

        assertThatThrownBy(() -> factory.getStrategy("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
    }
}
