package org.workfitai.authservice.exception;

/**
 * Exception thrown when OAuth provider operation fails
 */
public class OAuthProviderException extends RuntimeException {

    public OAuthProviderException(String message) {
        super(message);
    }

    public OAuthProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
