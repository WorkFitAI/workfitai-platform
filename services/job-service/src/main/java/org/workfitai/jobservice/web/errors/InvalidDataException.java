package org.workfitai.jobservice.web.errors;

public class InvalidDataException extends Exception {
    public InvalidDataException(String message) {
        super(message);
    }
}