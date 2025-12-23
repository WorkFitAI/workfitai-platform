package org.workfitai.authservice.exception;

/**
 * Exception thrown when OAuth state validation fails (CSRF protection)
 */
public class InvalidOAuthStateException extends RuntimeException {

    public InvalidOAuthStateException(String message) {
        super(message);
    }
}
