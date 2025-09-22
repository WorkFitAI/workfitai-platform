package org.workfitai.jobservice.config.errors;

public class JobConflictException extends RuntimeException {
    public JobConflictException(String message) {
        super(message);
    }
}
