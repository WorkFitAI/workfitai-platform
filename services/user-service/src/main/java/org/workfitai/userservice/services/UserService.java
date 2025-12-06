package org.workfitai.userservice.services;

import org.workfitai.userservice.dto.response.UserBaseResponse;

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
     * Find user ID by username.
     * Used to convert username from JWT to user ID for service operations.
     *
     * @param username the username
     * @return the user's UUID
     */
    UUID findUserIdByUsername(String username);
}
