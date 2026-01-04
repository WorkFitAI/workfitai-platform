package org.workfitai.cvservice.errors;

import org.springframework.http.HttpStatus;

public class CVConflictException extends BusinessException {
    public CVConflictException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
