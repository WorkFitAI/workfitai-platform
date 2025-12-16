package org.workfitai.authservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

@Data
@RefreshScope
@Configuration
@ConfigurationProperties(prefix = "password.policy")
public class PasswordPolicyConfig {

    private Integer minLength = 8;
    private Boolean requireUppercase = true;
    private Boolean requireLowercase = true;
    private Boolean requireDigit = true;
    private Boolean requireSpecialChar = true;
    private String specialChars = "@#$%^&+=!*()_-";
    private Integer maxAgeDays = 90;
    private Integer historyCount = 5;
    private Integer changeRateLimit = 3;
    private Integer changeRateWindow = 3600; // seconds
    private Integer forgotRateLimit = 5;
    private Integer forgotRateWindow = 86400; // seconds
}
