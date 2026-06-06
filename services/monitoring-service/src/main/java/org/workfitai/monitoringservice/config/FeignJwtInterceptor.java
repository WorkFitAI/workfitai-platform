package org.workfitai.monitoringservice.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Forwards the caller's Authorization header on every outbound Feign request.
 * Works for synchronous (non-@Async) controller call stacks only.
 */
@Component
public class FeignJwtInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return;
        String auth = attrs.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
        if (auth != null && !auth.isBlank()) {
            template.header(HttpHeaders.AUTHORIZATION, auth);
        }
    }
}
