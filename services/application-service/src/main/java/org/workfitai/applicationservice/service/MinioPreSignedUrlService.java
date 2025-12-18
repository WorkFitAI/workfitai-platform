package org.workfitai.applicationservice.service;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;
import org.workfitai.applicationservice.config.MinioConfig;
import org.workfitai.applicationservice.dto.response.PreSignedUrlResponse;
import org.workfitai.applicationservice.exception.FileStorageException;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for generating pre-signed URLs for secure CV downloads.
 *
 * Pre-signed URLs provide temporary access to MinIO objects without exposing
 * permanent credentials. URLs expire after 1 hour for security.
 *
 * Security considerations:
 * - URLs expire after 3600 seconds (1 hour)
 * - No direct MinIO credentials exposed
 * - Audit logging for all download requests
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MinioPreSignedUrlService {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    private static final int EXPIRY_SECONDS = 3600; // 1 hour

    /**
     * Generate a pre-signed URL for downloading a CV file.
     *
     * @param objectKey  The object key in MinIO (e.g., "username/appId/file.pdf")
     * @param fileName   Original filename for Content-Disposition header
     * @param fileSize   File size in bytes
     * @return PreSignedUrlResponse with temporary download URL
     * @throws FileStorageException if URL generation fails
     */
    public PreSignedUrlResponse generateDownloadUrl(String objectKey, String fileName, Long fileSize) {
        try {
            log.info("Generating pre-signed URL for: bucket={}, key={}", minioConfig.getBucket(), objectKey);

            // Generate pre-signed URL using internal endpoint
            String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(minioConfig.getBucket())
                            .object(objectKey)
                            .expiry(EXPIRY_SECONDS, TimeUnit.SECONDS)
                            .build());

            // Replace internal endpoint with external endpoint if configured
            // The signature in the URL remains valid because it's calculated based on
            // the request parameters (bucket, key, expiry), not the hostname
            if (minioConfig.getExternalEndpoint() != null && !minioConfig.getExternalEndpoint().isEmpty()) {
                String internalEndpoint = minioConfig.getEndpoint();
                url = url.replace(internalEndpoint, minioConfig.getExternalEndpoint());
                log.debug("Replaced internal endpoint {} with external endpoint {} in pre-signed URL",
                    internalEndpoint, minioConfig.getExternalEndpoint());
            }

            Instant expiresAt = Instant.now().plusSeconds(EXPIRY_SECONDS);

            log.info("Pre-signed URL generated successfully. Expires at: {}", expiresAt);

            return PreSignedUrlResponse.builder()
                    .url(url)
                    .expiresAt(expiresAt)
                    .fileName(fileName)
                    .fileSize(fileSize)
                    .build();

        } catch (Exception e) {
            log.error("Failed to generate pre-signed URL: {}", e.getMessage(), e);
            throw new FileStorageException("Failed to generate download URL: " + e.getMessage(), e);
        }
    }

    /**
     * Download file from MinIO as byte array.
     *
     * @param objectKey The object key in MinIO
     * @return File content as byte array
     * @throws FileStorageException if download fails
     */
    public byte[] downloadFile(String objectKey) {
        try {
            log.info("Downloading file from MinIO: bucket={}, key={}", minioConfig.getBucket(), objectKey);

            try (var stream = minioClient.getObject(
                    io.minio.GetObjectArgs.builder()
                            .bucket(minioConfig.getBucket())
                            .object(objectKey)
                            .build())) {

                byte[] content = stream.readAllBytes();
                log.info("Successfully downloaded file: {} bytes", content.length);
                return content;
            }

        } catch (Exception e) {
            log.error("Failed to download file from MinIO: {}", e.getMessage(), e);
            throw new FileStorageException("Failed to download file: " + e.getMessage(), e);
        }
    }

    /**
     * Extract object key from file URL.
     * URL format: http://minio:9000/bucket/username/folder/file.pdf
     * Returns: username/folder/file.pdf
     */
    public String extractObjectKey(String fileUrl) {
        String bucketPrefix = minioConfig.getBucket() + "/";
        int bucketIndex = fileUrl.indexOf(bucketPrefix);

        if (bucketIndex == -1) {
            throw new FileStorageException("Invalid file URL format: " + fileUrl);
        }

        return fileUrl.substring(bucketIndex + bucketPrefix.length());
    }
}
