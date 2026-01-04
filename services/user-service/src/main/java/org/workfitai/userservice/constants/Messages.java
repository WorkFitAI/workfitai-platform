package org.workfitai.userservice.constants;

/**
 * Constants for error and success messages used throughout the auth service.
 */
public final class Messages {

  private Messages() {
    // Prevent instantiation
  }

  // Error Messages
  public static final class Error {
    public static final String USERNAME_EMAIL_ALREADY_IN_USE = "Username or email already in use";
    public static final String VALIDATION_FAILED = "Validation failed";
    public static final String INVALID_REQUEST = "Invalid request";
    public static final String MALFORMED_JSON = "Malformed JSON";
    public static final String UNAUTHORIZED = "Unauthorized";
    public static final String INTERNAL_SERVER_ERROR = "Internal server error";
    public static final String DEFAULT_ERROR = "Error";
  }

  // Success Messages
  public static final class Success {
    public static final String OPERATION_SUCCESS = "Success";
  }

  public static final class Candidate {
    public static final String CREATED = "Candidate created successfully.";
    public static final String UPDATED = "Candidate updated successfully.";
    public static final String DELETED = "Candidate deleted successfully.";
    public static final String FETCHED = "Candidate fetched successfully.";
    public static final String LIST_FETCHED = "Candidates fetched successfully.";
    public static final String EXPERIENCE_STATS = "Candidate experience statistics fetched successfully.";
  }

  public static final class CandidateSkill {
    public static final String CREATED = "Skill added successfully.";
    public static final String UPDATED = "Skill updated successfully.";
    public static final String DELETED = "Skill deleted successfully.";
    public static final String FETCHED = "Skill fetched successfully.";
    public static final String LIST_FETCHED = "Candidate skills fetched successfully.";
    public static final String DUPLICATE = "Candidate already has this skill.";
    public static final String NOT_FOUND = "Candidate skill not found.";
  }

  public static final class HR {
    public static final String CREATED = "HR created successfully.";
    public static final String UPDATED = "HR updated successfully.";
    public static final String DELETED = "HR deleted successfully.";
    public static final String FETCHED = "HR fetched successfully.";
    public static final String LIST_FETCHED = "HR list fetched successfully.";
    public static final String APPROVED = "HR approval completed.";
  }

  public static final class Admin {
    public static final String CREATED = "Admin created successfully.";
    public static final String UPDATED = "Admin updated successfully.";
    public static final String DELETED = "Admin deleted successfully.";
    public static final String FETCHED = "Admin fetched successfully.";
    public static final String LIST_FETCHED = "Admin list fetched successfully.";

    private Admin() {
    }
  }

  public static final class User {
    public static final String PROFILE_FETCHED = "User profile fetched successfully.";
    public static final String FETCHED = "User retrieved successfully.";
    public static final String NOT_FOUND = "User not found.";
    public static final String EMAIL_EXISTS = "Email already exists in the system.";
    public static final String USERNAME_EXISTS = "Username already exists in the system.";
    public static final String PHONE_EXISTS = "Phone number already exists in the system.";
    public static final String INVALID_USER_ID = "Invalid user ID format.";
    public static final String ACCESS_DENIED = "You do not have permission to access this resource.";

    private User() {
    }
  }

  public static final class Profile {
    public static final String UPDATED = "Profile updated successfully.";
    public static final String UPDATE_FAILED = "Failed to update profile.";
    public static final String CANDIDATE_PROFILE_UPDATED = "Candidate profile updated successfully.";
    public static final String HR_PROFILE_UPDATED = "HR profile updated successfully.";
    public static final String ADMIN_PROFILE_UPDATED = "Admin profile updated successfully.";
    public static final String ROLE_MISMATCH = "Your account role does not match the requested profile type.";
    public static final String PROFILE_NOT_FOUND = "Profile not found for this user.";

    private Profile() {
    }
  }

  public static final class Common {
    public static final String SUCCESS = "Operation completed successfully.";
    public static final String FAILED = "Operation failed.";
  }
}
