package org.workfitai.authservice.exception;

/**
 * Exception thrown when trying to link an OAuth provider that's already linked
 */
public class ProviderAlreadyLinkedException extends RuntimeException {

    public ProviderAlreadyLinkedException(String message) {
        super(message);
    }
}
