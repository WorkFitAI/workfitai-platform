package org.workfitai.applicationservice.config;

import org.springframework.context.annotation.Bean;

import feign.RequestInterceptor;

/**
 * Feign configuration for internal service-to-service calls that do NOT
 * require JWT propagation (e.g., cv-service /internal/** endpoints).
 *
 * This class is intentionally NOT annotated with @Configuration — it is
 * referenced via the {@code configuration} attribute of @FeignClient to
 * apply it only to specific clients, preventing it from being picked up as
 * a global Feign config bean.
 *
 * Usage:
 * {@code @FeignClient(name = "cv-service", ..., configuration = InternalFeignConfig.class)}
 */
public class InternalFeignConfig {

    /**
     * No-op interceptor — internal endpoints are permitAll(), no auth header needed.
     */
    @Bean
    public RequestInterceptor noOpInterceptor() {
        return template -> {
            // Intentionally empty — do not forward any token
        };
    }
}
