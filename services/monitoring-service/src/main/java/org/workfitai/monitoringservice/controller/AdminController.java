package org.workfitai.monitoringservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.workfitai.monitoringservice.dto.*;
import org.workfitai.monitoringservice.service.AdminActivityService;

/**
 * Admin-only endpoints for user activity monitoring and system management.
 * All endpoints require ADMIN role.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminActivityService adminActivityService;

    /**
     * Get user activities for admin dashboard.
     * Returns meaningful user actions (login, job creation, applications, etc.)
     * NOT system logs or debug info.
     * 
     * @param username Filter by specific username (optional)
     * @param role     Filter by user role (CANDIDATE, HR, HR_MANAGER) (optional)
     * @param hours    Time range in hours (default 24)
     * @param page     Page number (0-indexed)
     * @param size     Page size
     * @return User activities with statistics
     */
    @GetMapping("/user-activities")
    public ResponseEntity<UserActivityResponse> getUserActivities(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.debug("Admin getting user activities - username: {}, role: {}, hours: {}",
                username, role, hours);

        UserActivityResponse response = adminActivityService.getUserActivities(
                username, role, hours, page, size);

        return ResponseEntity.ok(response);
    }

    /**
     * Get detailed activity history for a specific user.
     * 
     * @param username Username to query
     * @param hours    Time range in hours (default 24)
     * @param page     Page number
     * @param size     Page size
     * @return User's activity history
     */
    @GetMapping("/user-activities/{username}")
    public ResponseEntity<UserActivityResponse> getUserActivityByUsername(
            @PathVariable String username,
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        log.debug("Admin getting activity for user: {}", username);

        UserActivityResponse response = adminActivityService.getUserActivities(
                username, null, hours, page, size);

        return ResponseEntity.ok(response);
    }

    /**
     * Get currently online/active users.
     * Users who have activity in the last N minutes.
     * 
     * @param minutes Time range in minutes (default 15)
     * @return List of active users with their latest activity
     */
    @GetMapping("/online-users")
    public ResponseEntity<UserActivityResponse> getOnlineUsers(
            @RequestParam(defaultValue = "15") int minutes) {

        log.debug("Admin getting online users for last {} minutes", minutes);

        // Convert minutes to hours fraction
        double hoursDecimal = minutes / 60.0;
        int hours = Math.max(1, (int) Math.ceil(hoursDecimal));

        UserActivityResponse response = adminActivityService.getUserActivities(
                null, null, hours, 0, 100);

        return ResponseEntity.ok(response);
    }

    /**
     * Get user activity summary statistics.
     * 
     * @param hours Time range in hours (default 24)
     * @return Activity summary with statistics
     */
    @GetMapping("/activity-summary")
    public ResponseEntity<ActivitySummary> getActivitySummary(
            @RequestParam(defaultValue = "24") int hours) {

        log.debug("Admin getting activity summary for last {} hours", hours);

        ActivitySummary summary = adminActivityService.getActivitySummary(hours);

        return ResponseEntity.ok(summary);
    }
}
