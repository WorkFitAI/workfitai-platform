package org.workfitai.notificationservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

    @Mock
    private TemplateEngine templateEngine;

    private TemplateService templateService;

    private void init() {
        templateService = new TemplateService(templateEngine);
    }

    @Test
    void processTemplate_returnsRenderedHtml_onSuccess() {
        init();
        when(templateEngine.process(eq("otp-verification"), any(Context.class))).thenReturn("<html>OTP</html>");

        String result = templateService.processTemplate("otp-verification", Map.of("otp", "123456"));

        assertThat(result).isEqualTo("<html>OTP</html>");
    }

    @Test
    void processTemplate_returnsNull_whenEngineThrows() {
        init();
        when(templateEngine.process(eq("missing-template"), any(Context.class)))
                .thenThrow(new RuntimeException("template not found"));

        String result = templateService.processTemplate("missing-template", Map.of());

        assertThat(result).isNull();
    }

    @Test
    void processTemplate_handlesNullVariables() {
        init();
        when(templateEngine.process(eq("welcome"), any(Context.class))).thenReturn("<html>Welcome</html>");

        String result = templateService.processTemplate("welcome", null);

        assertThat(result).isEqualTo("<html>Welcome</html>");
    }
}
