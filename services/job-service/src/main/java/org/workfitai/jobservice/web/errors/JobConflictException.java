package org.workfitai.jobservice.web.errors;

public class JobConflictException extends RuntimeException {
    public JobConflictException(String message) {
        super(message);
    }
}
