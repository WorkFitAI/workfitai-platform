package org.workfitai.applicationservice.validation;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.workfitai.applicationservice.dto.request.CreateApplicationRequest;
import org.workfitai.applicationservice.exception.FileStorageException;

import lombok.extern.slf4j.Slf4j;

/**
 * Validates the CV file before upload.
 * Order: 2 (runs after duplicate check)
 * 
 * Validations:
 * - File is not empty
 * - File type is PDF
 * - File size is within limits (5MB)
 */
@Component
@Order(2)
@Slf4j
public class FileValidator implements ApplicationValidator {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final String ALLOWED_CONTENT_TYPE = "application/pdf";

    @Override
    public void validate(CreateApplicationRequest request, String username) {
        MultipartFile file = request.getCvPdfFile();

        log.debug("Validating CV file for user: {}", username);

        // Check if file exists
        if (file == null || file.isEmpty()) {
            throw new FileStorageException("CV file is required and cannot be empty");
        }

        // Check content type
        String contentType = file.getContentType();
        if (!ALLOWED_CONTENT_TYPE.equalsIgnoreCase(contentType)) {
            throw new FileStorageException(
                    String.format("Invalid file type. Expected PDF, got: %s", contentType));
        }

        // Check file size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new FileStorageException(
                    String.format("File size exceeds maximum allowed (5MB). Size: %.2f MB",
                            file.getSize() / (1024.0 * 1024.0)));
        }

        // Check filename
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new FileStorageException("File must have a valid filename");
        }

        if (!filename.toLowerCase().endsWith(".pdf")) {
            throw new FileStorageException("File must have .pdf extension");
        }

        log.debug("CV file validation passed: filename={}, size={} bytes", filename, file.getSize());
    }

    @Override
    public int getOrder() {
        return 2;
    }
}
