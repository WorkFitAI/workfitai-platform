package org.workfitai.notificationservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
     * @return Notification settings (email/push/sms enabled flags)
     */
    @GetMapping("/api/v1/internal/notification-settings/{email}")
    NotificationSettings getNotificationSettings(@PathVariable("email") String email);

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
