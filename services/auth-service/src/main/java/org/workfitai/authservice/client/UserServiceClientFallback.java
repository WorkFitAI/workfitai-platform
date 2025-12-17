package org.workfitai.authservice.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback implementation for UserServiceClient.
 * Returns false (allow registration) when user-service is unavailable.
 * This ensures auth-service can still function independently.
 */
@Component
@Slf4j
public class UserServiceClientFallback implements UserServiceClient {

    @Override
    public Boolean existsByEmail(String email) {
        log.warn("UserService unavailable, cannot check email existence for: {}", email);
        // Return false to allow registration when user-service is down
        // The duplicate will be caught when processing Kafka event
        return false;
    }

    @Override
    public Boolean existsByUsername(String username) {
        log.warn("UserService unavailable, cannot check username existence for: {}", username);
        return false;
    }

    @Override
    public Boolean checkAndReactivateAccount(String username) {
        log.error("UserService unavailable, cannot check reactivation status for: {}", username);
        // Return false to block login when user-service is down (safer approach)
        return false;
    }
}
