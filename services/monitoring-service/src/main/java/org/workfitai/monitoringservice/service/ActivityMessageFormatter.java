package org.workfitai.monitoringservice.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Formats technical log entries into human-readable messages for end users.
 *
 * Action-based messages are resolved from AuditPatternService (Redis-backed,
 * admin-configurable). Path-based fallbacks remain hardcoded here.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityMessageFormatter {

    private final AuditPatternService auditPatternService;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    /**
     * Format a log entry into a human-readable message.
     * 
     * @param method     HTTP method
     * @param path       Request path
     * @param action     Business action (CREATE, UPDATE, etc.)
     * @param entityType Entity type (Job, Application, etc.)
     * @param service    Source service
     * @return Formatted Vietnamese message
     */
    public String formatActivityMessage(String method, String path, String action, String entityType, String service) {
        // First try to format by action + entityType
        if (action != null && entityType != null) {
            String message = formatByAction(action, entityType);
            if (message != null) {
                return message;
            }
        }

        // Then try to format by path pattern
        String message = formatByPath(method, path);
        if (message != null) {
            return message;
        }

        // Fallback: generic message
        return formatGeneric(method, path, service);
    }

    /**
     * Resolve a human-readable message for an action+entityType combination.
     * Delegates to AuditPatternService so admins can customize messages at runtime.
     * Made package-private so AdminActivityService can call it directly.
     */
    String formatByAction(String action, String entityType) {
        if (action == null) {
            return null;
        }
        String key = (entityType != null && !entityType.isEmpty())
                ? action + "_" + entityType
                : action;
        return auditPatternService.resolveMessage(key).orElse(null);
    }

    /**
     * Format by path pattern.
     */
    private String formatByPath(String method, String path) {
        if (path == null)
            return null;

        // Auth paths
        if (path.contains("/login"))
            return "Đăng nhập vào hệ thống";
        if (path.contains("/logout") && !path.contains("/sessions"))
            return "Đăng xuất khỏi hệ thống";
        if (path.contains("/register"))
            return "Đăng ký tài khoản mới";
        if (path.contains("/forgot-password"))
            return "Yêu cầu đặt lại mật khẩu";
        if (path.contains("/reset-password"))
            return "Đặt lại mật khẩu";
        if (path.contains("/change-password"))
            return "Thay đổi mật khẩu";
        if (path.contains("/verify"))
            return "Xác thực tài khoản";
        if (path.contains("/enable-2fa"))
            return "Bật xác thực hai yếu tố";
        if (path.contains("/disable-2fa"))
            return "Tắt xác thực hai yếu tố";
        if (path.contains("/sessions") && method.equals("GET"))
            return "Xem danh sách phiên đăng nhập";
        if (path.contains("/sessions/all") && method.equals("DELETE"))
            return "Đăng xuất tất cả phiên làm việc";
        if (path.contains("/sessions") && method.equals("DELETE"))
            return "Đăng xuất phiên làm việc";

        // Profile paths
        if (path.contains("/profile") && method.equals("GET"))
            return "Xem hồ sơ cá nhân";
        if (path.contains("/profile") && method.equals("PUT"))
            return "Cập nhật hồ sơ cá nhân";
        if (path.contains("/avatar") && method.equals("POST"))
            return "Tải lên ảnh đại diện";
        if (path.contains("/avatar") && method.equals("DELETE"))
            return "Xóa ảnh đại diện";
        if (path.contains("/avatar"))
            return "Xem ảnh đại diện";
        if (path.contains("/notification-settings"))
            return "Cập nhật cài đặt thông báo";
        if (path.contains("/privacy-settings"))
            return "Cập nhật cài đặt riêng tư";
        if (path.contains("/deactivate"))
            return "Vô hiệu hóa tài khoản";
        if (path.contains("/delete-request"))
            return "Yêu cầu xóa tài khoản";

        // Job paths
        if (path.matches(".*/jobs$") && method.equals("GET"))
            return "Xem danh sách tin tuyển dụng";
        if (path.matches(".*/jobs$") && method.equals("POST"))
            return "Tạo tin tuyển dụng mới";
        if (path.matches(".*/jobs/[^/]+$") && method.equals("GET"))
            return "Xem chi tiết tin tuyển dụng";
        if (path.matches(".*/jobs/[^/]+$") && method.equals("PUT"))
            return "Cập nhật tin tuyển dụng";
        if (path.matches(".*/jobs/[^/]+$") && method.equals("DELETE"))
            return "Xóa tin tuyển dụng";
        if (path.contains("/jobs") && path.contains("/publish"))
            return "Đăng tin tuyển dụng";
        if (path.contains("/jobs") && path.contains("/search"))
            return "Tìm kiếm tin tuyển dụng";

        // Application paths
        if (path.matches(".*/applications$") && method.equals("GET"))
            return "Xem danh sách hồ sơ";
        if (path.matches(".*/applications$") && method.equals("POST"))
            return "Nộp hồ sơ ứng tuyển";
        if (path.matches(".*/applications/[^/]+$") && method.equals("GET"))
            return "Xem chi tiết hồ sơ";
        if (path.matches(".*/applications/[^/]+$") && method.equals("PUT"))
            return "Cập nhật hồ sơ";
        if (path.matches(".*/applications/[^/]+$") && method.equals("DELETE"))
            return "Rút hồ sơ ứng tuyển";
        if (path.contains("/applications") && path.contains("/status"))
            return "Cập nhật trạng thái hồ sơ";
        if (path.contains("/applications") && path.contains("/review"))
            return "Đánh giá hồ sơ";

        // CV paths
        if (path.contains("/cv") && method.equals("POST"))
            return "Tải lên CV";
        if (path.contains("/cv") && method.equals("PUT"))
            return "Cập nhật CV";
        if (path.contains("/cv") && method.equals("DELETE"))
            return "Xóa CV";
        if (path.contains("/cv") && method.equals("GET"))
            return "Xem CV";
        if (path.contains("/cv/download"))
            return "Tải xuống CV";

        // User management
        if (path.contains("/users") && method.equals("GET"))
            return "Xem danh sách người dùng";
        if (path.contains("/users") && method.equals("POST"))
            return "Tạo người dùng mới";
        if (path.contains("/approve-hr-manager"))
            return "Phê duyệt tài khoản HR Manager";
        if (path.contains("/approve-hr"))
            return "Phê duyệt tài khoản HR";
        if (path.contains("/approve"))
            return "Phê duyệt tài khoản";
        if (path.contains("/reject"))
            return "Từ chối tài khoản";
        if (path.contains("/block"))
            return "Khóa tài khoản";
        if (path.contains("/unblock"))
            return "Mở khóa tài khoản";
        if (path.contains("/admin/users"))
            return "Quản lý người dùng";
        if (path.contains("/admin/hr"))
            return "Quản lý HR";

        // Company
        if (path.contains("/company") && method.equals("GET"))
            return "Xem thông tin công ty";
        if (path.contains("/company") && method.equals("PUT"))
            return "Cập nhật thông tin công ty";

        // Reports/Analytics
        if (path.contains("/report") || path.contains("/analytics"))
            return "Xem báo cáo thống kê";
        if (path.contains("/dashboard"))
            return "Xem trang tổng quan";
        if (path.contains("/activity"))
            return "Xem hoạt động người dùng";

        return null;
    }

    /**
     * Generic fallback message.
     */
    private String formatGeneric(String method, String path, String service) {
        String action = switch (method) {
            case "GET" -> "Xem";
            case "POST" -> "Tạo mới";
            case "PUT", "PATCH" -> "Cập nhật";
            case "DELETE" -> "Xóa";
            default -> "Thao tác";
        };

        String resource = extractResourceFromPath(path);
        return String.format("%s %s", action, resource);
    }

    /**
     * Extract resource name from path.
     */
    private String extractResourceFromPath(String path) {
        if (path == null || path.isEmpty())
            return "tài nguyên";

        // Remove query params
        String cleanPath = path.split("\\?")[0];

        // Get last meaningful segment
        String[] segments = cleanPath.split("/");
        for (int i = segments.length - 1; i >= 0; i--) {
            String segment = segments[i];
            // Skip IDs (UUIDs, numbers)
            if (segment.matches("[a-f0-9-]{36}|\\d+"))
                continue;
            if (segment.matches("api|v1|v2"))
                continue;
            if (!segment.isEmpty()) {
                return translateResourceName(segment);
            }
        }

        return "tài nguyên";
    }

    /**
     * Translate English resource names to Vietnamese.
     */
    private String translateResourceName(String resource) {
        Map<String, String> translations = new HashMap<>();
        translations.put("jobs", "tin tuyển dụng");
        translations.put("applications", "hồ sơ ứng tuyển");
        translations.put("users", "người dùng");
        translations.put("profile", "hồ sơ cá nhân");
        translations.put("cv", "CV");
        translations.put("company", "công ty");
        translations.put("companies", "công ty");
        translations.put("reports", "báo cáo");
        translations.put("analytics", "thống kê");
        translations.put("dashboard", "trang tổng quan");
        translations.put("notifications", "thông báo");
        translations.put("settings", "cài đặt");

        return translations.getOrDefault(resource.toLowerCase(), resource);
    }

    /**
     * Format timestamp to Vietnamese format.
     */
    public String formatTimestamp(Instant timestamp) {
        if (timestamp == null)
            return "";
        return FORMATTER.format(timestamp);
    }

    /**
     * Format relative time (e.g., "5 phút trước", "2 giờ trước").
     */
    public String formatRelativeTime(Instant timestamp) {
        if (timestamp == null)
            return "";

        long seconds = Instant.now().getEpochSecond() - timestamp.getEpochSecond();

        if (seconds < 60)
            return "Vừa xong";
        if (seconds < 3600)
            return (seconds / 60) + " phút trước";
        if (seconds < 86400)
            return (seconds / 3600) + " giờ trước";
        if (seconds < 604800)
            return (seconds / 86400) + " ngày trước";

        return formatTimestamp(timestamp);
    }

    /**
     * Get icon/emoji for activity type.
     */
    public String getActivityIcon(String action, String entityType) {
        if (action == null)
            return "📝";

        return switch (action) {
            case "CREATE", "SUBMIT" -> "➕";
            case "UPDATE", "EDIT" -> "✏️";
            case "DELETE", "WITHDRAW" -> "🗑️";
            case "VIEW", "READ", "LIST" -> "👁️";
            case "APPROVE" -> "✅";
            case "REJECT" -> "❌";
            case "UPLOAD" -> "📤";
            case "DOWNLOAD" -> "📥";
            case "SEARCH", "FILTER" -> "🔍";
            case "LOGIN" -> "🔐";
            case "LOGOUT", "LOGOUT_ALL", "LOGOUT_SESSION" -> "🚪";
            case "ENABLE", "ENABLE_2FA" -> "🔓";
            case "DISABLE", "DISABLE_2FA" -> "🔒";
            case "BLOCK" -> "🚫";
            case "UNBLOCK" -> "✔️";
            case "PUBLISH" -> "📢";
            case "UNPUBLISH", "CLOSE" -> "📴";
            case "REOPEN" -> "🔄";
            case "EXPORT" -> "💾";
            case "INTERVIEW" -> "👥";
            case "OFFER" -> "💼";
            case "SHORTLIST" -> "⭐";
            case "REVIEW" -> "📊";
            case "DEACTIVATE" -> "⏸️";
            case "VERIFY" -> "✔️";
            default -> "📝";
        };
    }
}
