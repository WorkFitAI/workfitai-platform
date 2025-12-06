package org.workfitai.authservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign client for communicating with user-service.
 * Used to check if email already exists in user-service before registration.
 */
@FeignClient(name = "user-service", fallback = UserServiceClientFallback.class)
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
    @GetMapping("/api/v1/users/exists/username")
    Boolean existsByUsername(@RequestParam("username") String username);
}
