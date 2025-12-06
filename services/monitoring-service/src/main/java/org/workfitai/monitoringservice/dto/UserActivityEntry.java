package org.workfitai.monitoringservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Simplified DTO for user activity monitoring dashboard.
 * Shows what users are doing in the system without technical details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserActivityEntry {

    /**
     * Unique activity ID
     */
    private String id;

    /**
     * When the activity occurred
     */
    private Instant timestamp;

    /**
     * Username who performed the action
     */
    private String username;

    /**
     * User roles at the time of action
     */
    private String roles;

    /**
     * Which service handled the request
     */
    private String service;

    /**
     * HTTP method (GET, POST, PUT, DELETE)
     */
    private String method;

    /**
     * Request path (e.g., /api/jobs, /auth/login)
     */
    private String path;

    /**
     * Human-readable action description
     */
    private String action;

    /**
     * Request ID for tracing
     */
    private String requestId;

    /**
     * Log level (INFO, WARN, ERROR)
     */
    private String level;

    /**
     * Whether this was an error
     */
    private Boolean isError;

    /**
     * Convert from LogEntry to UserActivityEntry
     */
    public static UserActivityEntry fromLogEntry(LogEntry log) {
        String action = inferAction(log.getMethod(), log.getPath(), log.getMessage());
        boolean isError = "ERROR".equalsIgnoreCase(log.getLevel());

        return UserActivityEntry.builder()
                .id(log.getId())
                .timestamp(log.getTimestamp())
                .username(log.getUsername())
                .roles(log.getRoles())
                .service(log.getService())
                .method(log.getMethod())
                .path(log.getPath())
                .action(action)
                .requestId(log.getRequestId())
                .level(log.getLevel())
                .isError(isError ? true : null) // Only include if true
                .build();
    }

    /**
     * Infer a human-readable action from HTTP method and path
     */
    private static String inferAction(String method, String path, String message) {
        if (path == null || path.isBlank()) {
            // Use message as fallback
            return truncateMessage(message);
        }

        String normalizedPath = path.toLowerCase();
        String httpMethod = method != null ? method.toUpperCase() : "?";

        // Auth actions
        if (normalizedPath.contains("/auth/login")) {
            return "Đăng nhập hệ thống";
        }
        if (normalizedPath.contains("/auth/logout")) {
            return "Đăng xuất";
        }
        if (normalizedPath.contains("/auth/register")) {
            return "Đăng ký tài khoản";
        }
        if (normalizedPath.contains("/auth/refresh")) {
            return "Làm mới token";
        }
        if (normalizedPath.contains("/auth/verify-otp")) {
            return "Xác thực OTP";
        }

        // User actions
        if (normalizedPath.contains("/user/profile")) {
            if ("GET".equals(httpMethod)) {
                return "Xem thông tin cá nhân";
            }
            if ("PUT".equals(httpMethod) || "PATCH".equals(httpMethod)) {
                return "Cập nhật thông tin cá nhân";
            }
        }

        // Job actions
        if (normalizedPath.matches(".*/jobs/\\d+.*") || normalizedPath.matches(".*/jobs/[a-f0-9-]+.*")) {
            if ("GET".equals(httpMethod)) {
                return "Xem chi tiết công việc";
            }
            if ("PUT".equals(httpMethod) || "PATCH".equals(httpMethod)) {
                return "Cập nhật công việc";
            }
            if ("DELETE".equals(httpMethod)) {
                return "Xóa công việc";
            }
        }
        if (normalizedPath.contains("/jobs")) {
            if ("GET".equals(httpMethod)) {
                return "Xem danh sách công việc";
            }
            if ("POST".equals(httpMethod)) {
                return "Tạo công việc mới";
            }
        }

        // CV actions
        if (normalizedPath.contains("/cv")) {
            if ("GET".equals(httpMethod)) {
                return "Xem CV";
            }
            if ("POST".equals(httpMethod)) {
                return "Tạo/Upload CV";
            }
            if ("PUT".equals(httpMethod) || "PATCH".equals(httpMethod)) {
                return "Cập nhật CV";
            }
            if ("DELETE".equals(httpMethod)) {
                return "Xóa CV";
            }
        }

        // Application actions
        if (normalizedPath.contains("/application")) {
            if ("GET".equals(httpMethod)) {
                return "Xem đơn ứng tuyển";
            }
            if ("POST".equals(httpMethod)) {
                return "Nộp đơn ứng tuyển";
            }
            if ("PUT".equals(httpMethod) || "PATCH".equals(httpMethod)) {
                return "Cập nhật đơn ứng tuyển";
            }
        }

        // HR approval actions
        if (normalizedPath.contains("/approve")) {
            return "Phê duyệt";
        }
        if (normalizedPath.contains("/reject")) {
            return "Từ chối";
        }

        // Health check (can be filtered out in UI)
        if (normalizedPath.contains("/health") || normalizedPath.contains("/actuator")) {
            return "Health check";
        }

        // Default: HTTP METHOD + path
        return httpMethod + " " + truncatePath(path);
    }

    private static String truncatePath(String path) {
        if (path == null)
            return "";
        if (path.length() > 50) {
            return path.substring(0, 47) + "...";
        }
        return path;
    }

    private static String truncateMessage(String message) {
        if (message == null)
            return "Hoạt động không xác định";
        if (message.length() > 100) {
            return message.substring(0, 97) + "...";
        }
        return message;
    }
}
