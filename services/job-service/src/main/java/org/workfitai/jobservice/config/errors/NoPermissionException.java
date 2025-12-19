package org.workfitai.jobservice.config.errors;

import org.springframework.security.access.AccessDeniedException;

public class NoPermissionException extends AccessDeniedException {
    public NoPermissionException(String message) {
        super(message);
    }
}
