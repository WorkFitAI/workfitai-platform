package org.workfitai.apigateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Rate Limiting Configuration Properties
 * 
 * Supports:
 * - Global rate limits (per IP address)
 * - Per-user rate limits (for authenticated requests)
 * - Endpoint-specific rate limits
 */
@Configuration
@ConfigurationProperties(prefix = "app.rate-limit")
@Data
public class RateLimitConfig {

    private GlobalConfig global = new GlobalConfig();
    private PerUserConfig perUser = new PerUserConfig();
    private List<EndpointConfig> endpoints = new ArrayList<>();

    @Data
    public static class GlobalConfig {
        private boolean enabled = true;
        private int requestsPerSecond = 100;
        private int burstCapacity = 200;
    }

    @Data
    public static class PerUserConfig {
        private boolean enabled = true;
        private int requestsPerSecond = 50;
        private int burstCapacity = 100;
    }

    @Data
    public static class EndpointConfig {
        private String path;
        private int requestsPerMinute;
        private int burstCapacity;
    }
}
