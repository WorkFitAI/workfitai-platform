package org.workfitai.monitoringservice.dto;

/**
 * Represents one configurable audit log display pattern.
 *
 * key            - pattern key, e.g. "CREATE_Job" or "ENABLE_2FA"
 * message        - current display text (custom override or default)
 * defaultMessage - the out-of-the-box default shipped with the service
 * customized     - true when an admin has overridden the default
 */
public record AuditPatternDto(
        String key,
        String message,
        String defaultMessage,
        boolean customized
) {}
