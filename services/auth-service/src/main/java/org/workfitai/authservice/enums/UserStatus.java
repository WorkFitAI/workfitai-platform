package org.workfitai.authservice.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enum representing the status of users during registration flow.
 */
public enum UserStatus {
    PENDING("PENDING"), // User registered, waiting for OTP verification
    WAIT_APPROVED("WAIT_APPROVED"), // OTP verified, waiting for admin/HR_MANAGER approval
    ACTIVE("ACTIVE"), // User fully activated and can login
    INACTIVE("INACTIVE"); // User deactivated

    private final String statusName;

    UserStatus(String statusName) {
        this.statusName = statusName;
    }

    @JsonValue
    public String getStatusName() {
        return statusName;
    }

    @JsonCreator
    public static UserStatus fromString(String statusName) {
        for (UserStatus status : UserStatus.values()) {
            if (status.statusName.equalsIgnoreCase(statusName)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown status: " + statusName);
    }

    @Override
    public String toString() {
        return statusName;
    }
}