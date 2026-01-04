package org.workfitai.userservice.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.workfitai.userservice.dto.response.UserBaseResponse;

import java.util.List;
import java.util.UUID;

/**
 * Service for user-related operations across all user types.
 */
public interface UserService {

    /**
     * Get current user profile by user ID extracted from JWT token.
     *
     * @param userId the user ID from JWT token
     * @return user profile based on their role (CandidateResponse, HRResponse, or
     *         AdminResponse)
     */
    Object getCurrentUserProfile(UUID userId);

    /**
     * Get user by email.
     *
     * @param email the user's email
     * @return user base response
     */
    UserBaseResponse getByEmail(String email);

    /**
     * Get user by username.
     *
     * @param username the user's username
     * @return user base response
     */
    UserBaseResponse getByUsername(String username);

    /**
     * Get current user profile by username extracted from JWT token.
     *
     * @param username the username from JWT token (sub claim)
     * @return user profile based on their role (CandidateResponse, HRResponse, or
     *         AdminResponse)
     */
    Object getCurrentUserProfileByUsername(String username);

    /**
     * Check if email exists.
     */
    boolean existsByEmail(String email);

    /**
     * Check if username exists.
     */
    boolean existsByUsername(String username);

    /**
     * Check if phone number exists.
     */
    boolean existsByPhoneNumber(String phoneNumber);

    /**
     * Find user ID by username.
     * Used to convert username from JWT to user ID for service operations.
     *
     * @param username the username
     * @return the user's UUID
     */
    UUID findUserIdByUsername(String username);

    /**
     * Get users by list of usernames.
     * Used for bulk user info retrieval (e.g., for notifications).
     *
     * @param usernames list of usernames
     * @return list of user base responses
     */
    List<UserBaseResponse> getUsersByUsernames(List<String> usernames);

    /**
     * Check if deactivated account can be reactivated (within 30 days) and
     * auto-reactivate.
     * Called by auth-service during login.
     *
     * @param username the username to check
     * @return true if account was reactivated, false if beyond 30 days or deleted
     */
    boolean checkAndReactivateAccount(String username);

    /**
     * Search all users across all roles with pagination.
     * For admin user management dashboard.
     *
     * @param keyword  search keyword (username, email, fullName)
     * @param role     filter by role (optional)
     * @param pageable pagination parameters
     * @return page of user base responses
     */
    Page<UserBaseResponse> searchAllUsers(String keyword, String role, Pageable pageable);

    /**
     * Get basic user information by ID.
     *
     * @param userId user ID
     * @return user base response
     */
    UserBaseResponse getByUserId(UUID userId);

    UserBaseResponse getUserByUsername(String username);

    /**
     * Get full user profile with role-specific details.
     * Returns CandidateResponse, HRResponse, or AdminResponse based on role.
     * TODO: Requires getByUserId() methods in CandidateService, HRService,
     * AdminService
     *
     * @param userId user ID
     * @return role-specific response (Candidate/HR/Admin)
     */
    // Object getFullUserProfile(UUID userId);

    /**
     * Delete user (soft delete)
     *
     * @param userId user ID to delete
     */
    void deleteUser(UUID userId);

    /**
     * Block or unblock user account.
     *
     * @param userId  user ID
     * @param blocked true to block, false to unblock
     */
    void setUserBlockStatus(UUID userId, boolean blocked);

    /**
     * Block or unblock user account by username.
     *
     * @param username      the username
     * @param blocked       true to block, false to unblock
     * @param currentUserId current logged-in user ID (to prevent self-blocking)
     */
    void setUserBlockStatusByUsername(String username, boolean blocked, String currentUserId);

    /**
     * Delete user by username (soft delete).
     *
     * @param username      the username to delete
     * @param currentUserId current logged-in user ID (to prevent self-deletion)
     */
    void deleteUserByUsername(String username, String currentUserId);

    /**
     * Add OAuth provider to user's linked providers list.
     * Called by Kafka consumer when auth-service publishes OAuthAccountLinkedEvent
     *
     * @param username      the username
     * @param provider      the OAuth provider (GOOGLE | GITHUB)
     * @param providerEmail email associated with OAuth provider
     */
    void addOAuthProvider(String username, String provider, String providerEmail);

    /**
     * Remove OAuth provider from user's linked providers list.
     * Called by Kafka consumer when auth-service publishes
     * OAuthAccountUnlinkedEvent
     *
     * @param username the username
     * @param provider the OAuth provider to remove (GOOGLE | GITHUB)
     */
    void removeOAuthProvider(String username, String provider);
}
