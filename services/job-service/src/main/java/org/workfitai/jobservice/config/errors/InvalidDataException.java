package org.workfitai.jobservice.config.errors;

public class InvalidDataException extends Exception {
    public InvalidDataException(String message) {
        super(message);
    }
}