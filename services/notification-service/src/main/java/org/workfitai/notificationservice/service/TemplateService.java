package org.workfitai.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateService {

    private final TemplateEngine templateEngine;

    public String processTemplate(String templateName, Map<String, Object> variables) {
        try {
            Context context = new Context();
            context.setVariables(variables);

            return templateEngine.process(templateName, context);
        } catch (Exception e) {
            log.error("Error processing template {}: {}", templateName, e.getMessage());
            return null;
        }
    }
}