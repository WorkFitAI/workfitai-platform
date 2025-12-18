package org.workfitai.applicationservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.minio.MinioClient;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * MinIO configuration for CV file storage.
 *
 * Properties are loaded from application.yml under 'minio' prefix:
 * - minio.endpoint: MinIO server URL (internal Docker network URL)
 * - minio.external-endpoint: Public-facing MinIO URL (for pre-signed URLs)
 * - minio.access-key: Access key for authentication
 * - minio.secret-key: Secret key for authentication
 * - minio.bucket: Bucket name for CV files
 */
@Configuration
@ConfigurationProperties(prefix = "minio")
@Data
@Slf4j
public class MinioConfig {

    private String endpoint = "http://minio:9000";
    private String externalEndpoint; // Public-facing URL (e.g., http://localhost:9000)
    private String accessKey = "minioadmin";
    private String secretKey = "minioadmin";
    private String bucket = "cvs-files";

    @Bean
    public MinioClient minioClient() {
        log.info("Initializing MinIO client with endpoint: {}", endpoint);
        if (externalEndpoint != null && !externalEndpoint.isEmpty()) {
            log.info("External endpoint configured: {} (will be used for pre-signed URLs)", externalEndpoint);
        }
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
