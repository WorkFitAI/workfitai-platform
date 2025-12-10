package org.workfitai.applicationservice.exception;

/**
 * Exception thrown when file storage operations fail.
 * Used by FileStoragePort implementations.
 */
public class FileStorageException extends RuntimeException {

    public FileStorageException(String message) {
        super(message);
    }

    public FileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
