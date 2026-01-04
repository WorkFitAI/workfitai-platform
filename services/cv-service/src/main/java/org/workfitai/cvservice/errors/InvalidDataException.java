package org.workfitai.cvservice.errors;

import org.springframework.http.HttpStatus;

public class InvalidDataException extends BusinessException {
    public InvalidDataException(String message, HttpStatus status) {
        super(message, status);
    }
}
