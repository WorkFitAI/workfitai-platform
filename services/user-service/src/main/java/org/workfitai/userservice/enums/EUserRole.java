package org.workfitai.userservice.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Arrays;
import java.util.Optional;

/**
 * Enum representing the roles available in the system.
 */
public enum EUserRole {
  CANDIDATE("CANDIDATE"),
  HR("HR"),
  ADMIN("ADMIN");

  private final String displayName;

  EUserRole(String displayName) {
    this.displayName = displayName;
  }

  public static EUserRole fromDisplayName(String displayName) {
    return Arrays.stream(EUserRole.values())
        .filter(role -> role.displayName.equalsIgnoreCase(displayName))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Invalid role: " + displayName));
  }

  public static Optional<EUserRole> fromJsonSafe(String value) {
    try {
      return Optional.ofNullable(fromJson(value));
    } catch (Exception e) {
      return Optional.empty();
    }
  }


  @JsonCreator
  public static EUserRole fromJson(String value) {
    if (value == null) return null;
    for (EUserRole role : values()) {
      if (role.name().replace("_", "").equalsIgnoreCase(value.replace(" ", "").replace("_", "")) ||
          role.displayName.replace(" ", "").equalsIgnoreCase(value.replace(" ", "").replace("_", ""))) {
        return role;
      }
    }
    throw new IllegalArgumentException("Invalid role: " + value);
  }
}
