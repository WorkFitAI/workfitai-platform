package org.workfitai.applicationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of a file upload operation.
 * Contains the URL and metadata of the uploaded file.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResult {

    /**
     * Full URL to access the uploaded file.
     * Format: http://minio:9000/bucket/path/filename
     */
    private String fileUrl;

    /**
     * Original filename from the upload.
     */
    private String fileName;

    /**
     * Content type of the file (e.g., "application/pdf").
     */
    private String contentType;

    /**
     * Size of the file in bytes.
     */
    private long fileSize;

    /**
     * Object key in MinIO (path within bucket).
     */
    private String objectKey;
}
