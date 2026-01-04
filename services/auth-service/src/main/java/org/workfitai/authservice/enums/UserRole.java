package org.workfitai.authservice.enums;

/**
 * Enum representing the roles available in the system.
 */
public enum UserRole {
    CANDIDATE("CANDIDATE"),
    HR("HR"),
    HR_MANAGER("HR_MANAGER"),
    ADMIN("ADMIN");

    private final String roleName;

    UserRole(String roleName) {
        this.roleName = roleName;
    }

    /**
     * Get the role name as a string.
     * 
     * @return the role name
     */
    public String getRoleName() {
        return roleName;
    }

    /**
     * Convert a string role name to the corresponding enum.
     * 
     * @param roleName the role name string
     * @return the corresponding UserRole enum
     * @throws IllegalArgumentException if the role name is not valid
     */
    public static UserRole fromString(String roleName) {
        for (UserRole role : UserRole.values()) {
            if (role.roleName.equalsIgnoreCase(roleName)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown role: " + roleName);
    }

    @Override
    public String toString() {
        return roleName;
    }
}
