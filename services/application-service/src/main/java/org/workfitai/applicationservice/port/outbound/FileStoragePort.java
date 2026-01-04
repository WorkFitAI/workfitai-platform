package org.workfitai.applicationservice.port.outbound;

import org.springframework.web.multipart.MultipartFile;
import org.workfitai.applicationservice.dto.FileUploadResult;

/**
 * Outbound port for file storage operations.
 * Part of Hexagonal Architecture - defines interface for infrastructure
 * adapter.
 * 
 * Implementation: MinioFileStorageAdapter (stores files in MinIO)
 */
public interface FileStoragePort {

    /**
     * Upload a file to storage.
     *
     * @param file     The multipart file to upload
     * @param username Owner of the file (used in path)
     * @param folder   Subfolder for organization (e.g., applicationId)
     * @return FileUploadResult containing URL and metadata
     * @throws FileStorageException if upload fails
     */
    FileUploadResult uploadFile(MultipartFile file, String username, String folder);

    /**
     * Delete a file from storage.
     *
     * @param fileUrl The full URL of the file to delete
     * @throws FileStorageException if deletion fails
     */
    void deleteFile(String fileUrl);

    /**
     * Check if a file exists.
     *
     * @param fileUrl The full URL to check
     * @return true if file exists, false otherwise
     */
    boolean fileExists(String fileUrl);
}
