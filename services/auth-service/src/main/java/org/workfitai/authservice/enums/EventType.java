package org.workfitai.authservice.enums;

/**
 * Event types for Kafka events
 */
public enum EventType {
    // User events
    USER_REGISTERED,
    USER_APPROVED,
    USER_REJECTED,

    // OAuth events
    OAUTH_LOGIN,
    OAUTH_ACCOUNT_LINKED,
    OAUTH_ACCOUNT_UNLINKED,

    // Company events
    COMPANY_CREATED,

    // Security events
    PASSWORD_CHANGED,
    TWO_FA_ENABLED,
    TWO_FA_DISABLED,

    // Notification events
    EMAIL_NOTIFICATION
}
