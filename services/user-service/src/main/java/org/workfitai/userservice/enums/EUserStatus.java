package org.workfitai.userservice.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Arrays;
import java.util.Optional;

public enum EUserStatus {
  PENDING("PENDING"), // User registered, waiting for OTP verification
  WAIT_APPROVED("WAIT_APPROVED"), // OTP verified, waiting for admin/HR_MANAGER approval
  ACTIVE("ACTIVE"), // User fully activated
  SUSPENDED("SUSPENDED"),
  DEACTIVATED("DEACTIVATED"),
  DELETED("DELETED");

  private final String displayName;

  EUserStatus(String displayName) {
    this.displayName = displayName;
  }

  public static EUserStatus fromDisplayName(String displayName) {
    return Arrays.stream(EUserStatus.values())
        .filter(status -> status.displayName.equalsIgnoreCase(displayName))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Invalid role: " + displayName));
  }

  public static Optional<EUserStatus> fromJsonSafe(String value) {
    try {
      return Optional.ofNullable(fromJson(value));
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  @JsonCreator
  public static EUserStatus fromJson(String value) {
    if (value == null)
      return null;
    for (EUserStatus status : values()) {
      if (status.name().replace("_", "").equalsIgnoreCase(value.replace(" ", "").replace("_", "")) ||
          status.displayName.replace(" ", "").equalsIgnoreCase(value.replace(" ", "").replace("_", ""))) {
        return status;
      }
    }
    throw new IllegalArgumentException("Invalid role: " + value);
  }
}
