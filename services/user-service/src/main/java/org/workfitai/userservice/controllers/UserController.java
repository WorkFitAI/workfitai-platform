package org.workfitai.userservice.controllers;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.workfitai.userservice.constants.Messages;
import org.workfitai.userservice.dto.response.ResponseData;
import org.workfitai.userservice.dto.response.UserBaseResponse;
import org.workfitai.userservice.services.UserService;

/**
 * Controller for user-related operations.
 * Provides endpoints for user lookup and validation.
 * 
 * Note: Profile operations (get/update own profile) are in
 * UserProfileController.
 */
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Get user by email.
     *
     * @param email the user's email
     * @return user base response
     */
    @GetMapping("/by-email")
    public ResponseEntity<ResponseData<UserBaseResponse>> getByEmail(
            @RequestParam("email") String email) {
        UserBaseResponse user = userService.getByEmail(email);
        return ResponseEntity.ok(ResponseData.success(Messages.User.FETCHED, user));
    }

    /**
     * Get user by username.
     *
     * @param username the user's username
     * @return user base response
     */
    @GetMapping("/by-username")
    public ResponseEntity<ResponseData<UserBaseResponse>> getByUsername(
            @RequestParam("username") String username) {
        UserBaseResponse user = userService.getByUsername(username);
        return ResponseEntity.ok(ResponseData.success(Messages.User.FETCHED, user));
    }

    /**
     * Get users by list of usernames.
     * Used for bulk user info retrieval.
     *
     * @param usernames comma-separated list of usernames
     * @return list of user base responses
     */
    @GetMapping("/by-usernames")
    public ResponseEntity<ResponseData<List<UserBaseResponse>>> getByUsernames(
            @RequestParam("usernames") java.util.List<String> usernames) {
        List<UserBaseResponse> users = userService.getUsersByUsernames(usernames);
        return ResponseEntity.ok(ResponseData.success(Messages.User.FETCHED, users));
    }

    /**
     * Check if an email already exists in user-service.
     * Used by auth-service during registration to ensure data consistency.
     *
     * @param email the email to check
     * @return true if email exists, false otherwise
     */
    @GetMapping("/exists/email")
    public ResponseEntity<Boolean> existsByEmail(@RequestParam("email") String email) {
        boolean exists = userService.existsByEmail(email);
        return ResponseEntity.ok(exists);
    }

    /**
     * Check if a username already exists in user-service.
     * Used by auth-service during registration to ensure data consistency.
     *
     * @param username the username to check
     * @return true if username exists, false otherwise
     */
    @GetMapping("/exists/username")
    public ResponseEntity<Boolean> existsByUsername(@RequestParam("username") String username) {
        boolean exists = userService.existsByUsername(username);
        return ResponseEntity.ok(exists);
    }
}
