package org.workfitai.apigateway.config;

import io.micrometer.context.ContextRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Hooks;

@Configuration
public class ReactorMdcConfig {
    @PostConstruct
    public void init() {
        ContextRegistry.getInstance().registerThreadLocalAccessor(new Slf4jMdcAccessor());
        Hooks.enableAutomaticContextPropagation();
    }
}