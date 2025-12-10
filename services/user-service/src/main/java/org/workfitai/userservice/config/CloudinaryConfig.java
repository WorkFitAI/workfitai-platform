package org.workfitai.userservice.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Data
@RefreshScope
@Configuration
@ConfigurationProperties(prefix = "cloudinary")
public class CloudinaryConfig {

    private String cloudName;
    private String apiKey;
    private String apiSecret;
    private String uploadPreset;
    private String folder = "avatars";
    private Long maxFileSize = 5242880L; // 5MB
    private String allowedFormats = "jpg,jpeg,png,webp";

    // Transformation settings
    private Transformation transformation = new Transformation();

    @Data
    public static class Transformation {
        private Integer width = 400;
        private Integer height = 400;
        private String crop = "fill";
        private String gravity = "face";
        private String quality = "auto";
    }

    @Bean
    @RefreshScope
    public Cloudinary cloudinary() {
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true));
    }
}
