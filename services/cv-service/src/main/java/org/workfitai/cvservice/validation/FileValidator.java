package org.workfitai.cvservice.validation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.workfitai.cvservice.errors.InvalidDataException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import static org.workfitai.cvservice.constant.ErrorConst.*;

/**
 * File Validator
 * Validates uploaded CV files for type, size, and content
 */
@Slf4j
public class FileValidator {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final long MIN_FILE_SIZE = 1024; // 1KB minimum
    private static final String ALLOWED_CONTENT_TYPE = "application/pdf";
    private static final String[] ALLOWED_EXTENSIONS = {".pdf"};

    // PDF magic bytes (PDF file signature)
    private static final byte[] PDF_MAGIC_BYTES = {0x25, 0x50, 0x44, 0x46}; // %PDF

    /**
     * Validate uploaded file
     * Checks: null/empty, size limits, content type, file extension, magic bytes
     *
     * @param file Uploaded file
     * @throws InvalidDataException if validation fails
     */
    public static void validate(MultipartFile file) throws InvalidDataException {
        log.debug("Validating uploaded file: {}", file != null ? file.getOriginalFilename() : "null");

        // Check if file exists
        if (file == null || file.isEmpty()) {
            throw new InvalidDataException(CV_EMPTY_FILE, HttpStatus.BAD_REQUEST);
        }

        // Check file size (too small)
        if (file.getSize() < MIN_FILE_SIZE) {
            throw new InvalidDataException("File too small. Minimum size: 1KB", HttpStatus.BAD_REQUEST);
        }

        // Check file size (too large)
        if (file.getSize() > MAX_FILE_SIZE) {
            log.debug("File size {} exceeds maximum of {} bytes", file.getSize(), MAX_FILE_SIZE);
            throw new InvalidDataException(
                String.format(CV_FILE_EXCEED + ". Maximum size: 10MB, received: %.2f MB",
                    file.getSize() / (1024.0 * 1024.0)),
                HttpStatus.REQUEST_ENTITY_TOO_LARGE
            );
        }

        // Check content type
        String contentType = file.getContentType();
        if (!ALLOWED_CONTENT_TYPE.equals(contentType)) {
            throw new InvalidDataException(
                CV_FILE_WRONG_TYPE + ". Expected: application/pdf, received: " + contentType,
                HttpStatus.UNSUPPORTED_MEDIA_TYPE
            );
        }

        // Check file extension
        String filename = file.getOriginalFilename();
        if (filename == null || !hasAllowedExtension(filename)) {
            throw new InvalidDataException(
                "Invalid file extension. Only PDF files are allowed",
                HttpStatus.UNSUPPORTED_MEDIA_TYPE
            );
        }

        // Verify PDF magic bytes (prevent file type spoofing)
        validatePDFMagicBytes(file);

        log.debug("File validation successful: {}", filename);
    }

    /**
     * Check if filename has allowed extension
     */
    private static boolean hasAllowedExtension(String filename) {
        String lowerFilename = filename.toLowerCase();
        return Arrays.stream(ALLOWED_EXTENSIONS)
                .anyMatch(lowerFilename::endsWith);
    }

    /**
     * Validate PDF file by checking magic bytes
     * Prevents malicious files with fake .pdf extension
     */
    private static void validatePDFMagicBytes(MultipartFile file) throws InvalidDataException {
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[4];
            int bytesRead = is.read(header);

            if (bytesRead < 4) {
                throw new InvalidDataException(
                    "Invalid PDF file: File too small to be a valid PDF",
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE
                );
            }

            if (!Arrays.equals(header, PDF_MAGIC_BYTES)) {
                log.warn("File {} has invalid PDF magic bytes: {}",
                    file.getOriginalFilename(), Arrays.toString(header));
                throw new InvalidDataException(
                    "Invalid PDF file: File signature does not match PDF format",
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE
                );
            }
        } catch (IOException e) {
            log.error("Error reading file magic bytes: {}", e.getMessage(), e);
            throw new InvalidDataException(
                "Error validating file format",
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }
}