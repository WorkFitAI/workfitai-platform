package org.workfitai.authservice.exception;

/**
 * Exception thrown when trying to unlink the last authentication method
 */
public class CannotUnlinkLastAuthMethodException extends RuntimeException {

    public CannotUnlinkLastAuthMethodException(String message) {
        super(message);
    }
}
