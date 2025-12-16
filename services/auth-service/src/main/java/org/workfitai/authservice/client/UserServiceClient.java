package org.workfitai.authservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign client for communicating with user-service.
 * Used to check if email already exists in user-service before registration.
 * Service name 'user' matches spring.cloud.consul.discovery.service-name in
 * user-service config.
 */
@FeignClient(name = "user", fallback = UserServiceClientFallback.class)
public interface UserServiceClient {

    /**
     * Check if an email already exists in user-service.
     * 
     * @param email the email to check
     * @return true if email exists, false otherwise
     */
    @GetMapping("/exists/email")
    Boolean existsByEmail(@RequestParam("email") String email);

    /**
     * Check if a username already exists in user-service.
     * 
     * @param username the username to check
     * @return true if username exists, false otherwise
     */
    @GetMapping("/exists/username")
    Boolean existsByUsername(@RequestParam("username") String username);

    /**
     * Check if account can be reactivated (within 30 days) and auto-reactivate it.
     * 
     * @param username the username to check
     * @return true if account was reactivated, false if beyond 30 days
     */
    @GetMapping("/internal/check-reactivate")
    Boolean checkAndReactivateAccount(@RequestParam("username") String username);
}
