package org.workfitai.applicationservice.adapter.outbound;

import java.io.InputStream;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.workfitai.applicationservice.config.MinioConfig;
import org.workfitai.applicationservice.dto.FileUploadResult;
import org.workfitai.applicationservice.exception.FileStorageException;
import org.workfitai.applicationservice.port.outbound.FileStoragePort;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * MinIO implementation of FileStoragePort.
 * Part of Hexagonal Architecture - infrastructure adapter.
 * 
 * File path format: {username}/{folder}/{uuid}_{originalFilename}
 * Example: john_doe/app123/550e8400_resume.pdf
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MinioFileStorageAdapter implements FileStoragePort {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    @PostConstruct
    public void init() {
        try {
            boolean bucketExists = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(minioConfig.getBucket())
                            .build());

            if (!bucketExists) {
                log.info("Creating MinIO bucket: {}", minioConfig.getBucket());
                minioClient.makeBucket(
                        MakeBucketArgs.builder()
                                .bucket(minioConfig.getBucket())
                                .build());
                log.info("MinIO bucket created successfully");
            } else {
                log.info("MinIO bucket already exists: {}", minioConfig.getBucket());
            }
        } catch (Exception e) {
            log.error("Failed to initialize MinIO bucket: {}", e.getMessage());
            // Don't fail startup - bucket creation can be retried on first upload
        }
    }

    @Override
    public FileUploadResult uploadFile(MultipartFile file, String username, String folder) {
        validateFile(file);

        String originalFilename = file.getOriginalFilename();
        String uniqueFilename = UUID.randomUUID().toString().substring(0, 8) + "_" + originalFilename;
        String objectKey = String.format("%s/%s/%s", username, folder, uniqueFilename);

        try (InputStream inputStream = file.getInputStream()) {
            log.info("Uploading file to MinIO: bucket={}, key={}", minioConfig.getBucket(), objectKey);

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioConfig.getBucket())
                            .object(objectKey)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build());

            String fileUrl = String.format("%s/%s/%s",
                    minioConfig.getEndpoint(),
                    minioConfig.getBucket(),
                    objectKey);

            log.info("File uploaded successfully: {}", fileUrl);

            return FileUploadResult.builder()
                    .fileUrl(fileUrl)
                    .fileName(originalFilename)
                    .contentType(file.getContentType())
                    .fileSize(file.getSize())
                    .objectKey(objectKey)
                    .build();

        } catch (Exception e) {
            log.error("Failed to upload file to MinIO: {}", e.getMessage(), e);
            throw new FileStorageException("Failed to upload file: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteFile(String fileUrl) {
        try {
            String objectKey = extractObjectKey(fileUrl);
            log.info("Deleting file from MinIO: bucket={}, key={}", minioConfig.getBucket(), objectKey);

            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioConfig.getBucket())
                            .object(objectKey)
                            .build());

            log.info("File deleted successfully: {}", objectKey);

        } catch (Exception e) {
            log.error("Failed to delete file from MinIO: {}", e.getMessage(), e);
            throw new FileStorageException("Failed to delete file: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean fileExists(String fileUrl) {
        try {
            String objectKey = extractObjectKey(fileUrl);

            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(minioConfig.getBucket())
                            .object(objectKey)
                            .build());

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileStorageException("File is empty or null");
        }

        // Validate file type (PDF only)
        String contentType = file.getContentType();
        if (!"application/pdf".equalsIgnoreCase(contentType)) {
            throw new FileStorageException("Only PDF files are allowed. Received: " + contentType);
        }

        // Validate file size (max 5MB)
        long maxSize = 5 * 1024 * 1024; // 5MB
        if (file.getSize() > maxSize) {
            throw new FileStorageException("File size exceeds maximum allowed (5MB). Size: " + file.getSize());
        }
    }

    /**
     * Extract object key from full URL.
     * URL format: http://minio:9000/bucket/path/to/file
     * Returns: path/to/file
     */
    private String extractObjectKey(String fileUrl) {
        String bucketPrefix = minioConfig.getBucket() + "/";
        int bucketIndex = fileUrl.indexOf(bucketPrefix);

        if (bucketIndex == -1) {
            throw new FileStorageException("Invalid file URL format: " + fileUrl);
        }

        return fileUrl.substring(bucketIndex + bucketPrefix.length());
    }
}
