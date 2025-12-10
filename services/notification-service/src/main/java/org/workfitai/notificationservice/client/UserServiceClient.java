package org.workfitai.notificationservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * Feign client for user-service communication.
 */
@FeignClient(
    name = "user-service",
    url = "${service.user.url:http://user-service:9001}",
    path = "/api/v1/users")
public interface UserServiceClient {

    /**
     * Get user details by username.
     *
     * @param username The username to lookup
     * @return User details including email
     */
    @GetMapping("/by-username")
    Map<String, Object> getUserByUsername(@RequestParam("username") String username);
}
