package org.workfitai.applicationservice.client;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.workfitai.applicationservice.dto.response.RestResponse;

/**
 * Feign client for user-service API calls.
 *
 * Used for:
 * - Fetching user details (full name, email) for notifications
 * - Bulk user info retrieval by username list
 *
 * Service Discovery:
 * - Uses Consul service name "user" (matches
 * spring.cloud.consul.discovery.service-name)
 * - In Docker: Consul discovers user-service automatically
 * - In Local: Can override with user.url property
 *
 * Path Configuration:
 * - Feign client name "user" routes to the service via Consul
 * - Base path is root ("/") as UserController has no @RequestMapping prefix
 * - Final URL: http://user/by-username?username=...
 */
@FeignClient(name = "user")
public interface UserServiceClient {

    /**
     * Get user by username.
     *
     * Endpoint: GET /by-username?username={username}
     *
     * Response format:
     * {
     * "statusCode": 200,
     * "message": "User fetched successfully",
     * "data": {
     * "userId": "uuid",
     * "fullName": "John Doe",
     * "email": "john.doe@example.com",
     * "phoneNumber": "+84123456789",
     * "userRole": "HR",
     * "userStatus": "ACTIVE",
     * "lastLogin": "2025-12-08T10:00:00Z",
     * "createdDate": "2025-01-01T00:00:00Z"
     * }
     * }
     *
     * @param username Username to fetch
     * @return User base response wrapped in ResponseData
     */
    @GetMapping("/by-username")
    RestResponse<UserInfo> getUserByUsername(@RequestParam("username") String username);

    /**
     * Get multiple users by username list.
     *
     * Endpoint: GET /by-usernames?usernames=user1,user2,user3
     *
     * Response format:
     * {
     * "statusCode": 200,
     * "message": "Users fetched successfully",
     * "data": [
     * { "userId": "uuid1", "fullName": "John Doe", "email": "john@example.com", ...
     * },
     * { "userId": "uuid2", "fullName": "Jane Smith", "email": "jane@example.com",
     * ... }
     * ]
     * }
     *
     * @param usernames List of usernames
     * @return List of user base responses wrapped in ResponseData
     */
    @GetMapping("/by-usernames")
    RestResponse<List<UserInfo>> getUsersByUsernames(@RequestParam("usernames") List<String> usernames);

    /**
     * Get all users in a specific company by companyId.
     *
     * Endpoint: GET /by-company?companyId={companyId}
     *
     * Response format:
     * {
     * "statusCode": 200,
     * "message": "Users fetched successfully",
     * "data": [
     * { "userId": "uuid1", "fullName": "John Doe", "email": "john@example.com",
     * "userRole": "HR", ... },
     * { "userId": "uuid2", "fullName": "Jane Manager", "email": "jane@example.com",
     * "userRole": "HR_MANAGER", ... }
     * ]
     * }
     *
     * @param companyId Company ID to filter users
     * @return List of users in the company wrapped in RestResponse
     */
    @GetMapping("/by-company")
    RestResponse<List<UserInfo>> getUsersByCompanyId(@RequestParam("companyId") String companyId);

    /**
     * DTO for user information from user-service.
     * Minimal fields needed for notifications and company validation.
     */
    record UserInfo(
            String userId,
            String username,
            String fullName,
            String email,
            String phoneNumber,
            String userRole,
            String companyId) {
    }
}
