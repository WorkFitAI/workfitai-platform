package org.workfitai.notificationservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.workfitai.notificationservice.dto.HrNotificationSettings;
import org.workfitai.notificationservice.dto.NotificationSettings;

/**
 * Feign client for user-service communication.
 * Uses service discovery via Consul to find user-service instances.
 * Service name 'user' matches spring.cloud.consul.discovery.service-name in
 * user-service config.
 */
@FeignClient(name = "user", fallback = UserServiceClientFallback.class)
public interface UserServiceClient {

    /**
     * Get notification settings by email (internal API).
     *
     * @param email The user's email address
     * @return Notification settings (per-type email/push flags)
     */
    @GetMapping("/api/v1/internal/notification-settings/{email}")
    NotificationSettings getNotificationSettings(@PathVariable("email") String email);

    /**
     * Get HR notification settings by email (internal API).
     *
     * @param email The HR user's email address
     * @return HR notification preferences (new-application / job-expiry)
     */
    @GetMapping("/api/v1/internal/hr-notification-settings/{email}")
    HrNotificationSettings getHrNotificationSettings(@PathVariable("email") String email);

    /**
     * Get user details by username.
     *
     * @param username The username to lookup
     * @return User details wrapped in ResponseData
     */
    @GetMapping("/by-username")
    java.util.Map<String, Object> getUserByUsername(
            @org.springframework.web.bind.annotation.RequestParam("username") String username);
}
