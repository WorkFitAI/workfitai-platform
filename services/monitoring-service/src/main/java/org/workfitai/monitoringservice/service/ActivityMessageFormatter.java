package org.workfitai.monitoringservice.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Formats technical log entries into human-readable messages for end users.
 * 
 * Converts raw API calls and technical logs into understandable Vietnamese
 * descriptions
 * suitable for admin dashboards and user activity reports.
 */
@Service
@Slf4j
public class ActivityMessageFormatter {

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
     * Format by business action.
     * Made package-private so it can be used by AdminActivityService.
     */
    String formatByAction(String action, String entityType) {
        // Handle null/empty cases
        if (action == null) {
            return null;
        }

        // Build key - if entityType is empty/null, just use action alone
        String key = (entityType != null && !entityType.isEmpty())
                ? action + "_" + entityType
                : action;

        Map<String, String> actionMessages = new HashMap<>();

        // Job actions
        actionMessages.put("CREATE_Job", "Tạo tin tuyển dụng mới");
        actionMessages.put("UPDATE_Job", "Cập nhật tin tuyển dụng");
        actionMessages.put("DELETE_Job", "Xóa tin tuyển dụng");
        actionMessages.put("VIEW_Job", "Xem chi tiết tin tuyển dụng");
        actionMessages.put("PUBLISH_Job", "Đăng tin tuyển dụng");
        actionMessages.put("UNPUBLISH_Job", "Gỡ tin tuyển dụng");
        actionMessages.put("CLOSE_Job", "Đóng tin tuyển dụng");
        actionMessages.put("REOPEN_Job", "Mở lại tin tuyển dụng");
        actionMessages.put("SEARCH_Job", "Tìm kiếm tin tuyển dụng");
        actionMessages.put("FILTER_Job", "Lọc tin tuyển dụng");
        actionMessages.put("EXPORT_Job", "Xuất danh sách tin tuyển dụng");

        // Application actions
        actionMessages.put("CREATE_Application", "Tạo hồ sơ ứng tuyển");
        actionMessages.put("SUBMIT_Application", "Nộp hồ sơ ứng tuyển");
        actionMessages.put("UPDATE_Application", "Cập nhật hồ sơ ứng tuyển");
        actionMessages.put("WITHDRAW_Application", "Rút hồ sơ ứng tuyển");
        actionMessages.put("VIEW_Application", "Xem hồ sơ ứng tuyển");
        actionMessages.put("LIST_Application", "Xem danh sách hồ sơ ứng tuyển");
        actionMessages.put("APPROVE_Application", "Duyệt hồ sơ ứng tuyển");
        actionMessages.put("REJECT_Application", "Từ chối hồ sơ ứng tuyển");
        actionMessages.put("REVIEW_Application", "Đánh giá hồ sơ ứng tuyển");
        actionMessages.put("SHORTLIST_Application", "Chọn vào danh sách rút gọn");
        actionMessages.put("INTERVIEW_Application", "Mời phỏng vấn");
        actionMessages.put("OFFER_Application", "Gửi thư mời nhận việc");
        actionMessages.put("SEARCH_Application", "Tìm kiếm hồ sơ ứng tuyển");
        actionMessages.put("FILTER_Application", "Lọc hồ sơ ứng tuyển");
        actionMessages.put("EXPORT_Application", "Xuất danh sách hồ sơ");

        // Auth/Security actions (standalone - no entity type needed)
        actionMessages.put("ENABLE_2FA", "Bật xác thực hai yếu tố");
        actionMessages.put("DISABLE_2FA", "Tắt xác thực hai yếu tố");
        actionMessages.put("VERIFY_2FA", "Xác thực mã 2FA");
        actionMessages.put("VIEW_Sessions", "Xem danh sách phiên đăng nhập");
        actionMessages.put("LOGOUT_Session", "Đăng xuất phiên làm việc");
        actionMessages.put("LOGOUT_ALL", "Đăng xuất tất cả phiên làm việc");

        // User/Profile actions
        actionMessages.put("CREATE_User", "Tạo tài khoản người dùng");
        actionMessages.put("UPDATE_User", "Cập nhật thông tin người dùng");
        actionMessages.put("DELETE_User", "Xóa tài khoản người dùng");
        actionMessages.put("VIEW_User", "Xem thông tin người dùng");
        actionMessages.put("BLOCK_User", "Khóa tài khoản người dùng");
        actionMessages.put("UNBLOCK_User", "Mở khóa tài khoản người dùng");
        actionMessages.put("APPROVE_User", "Phê duyệt tài khoản người dùng");
        actionMessages.put("DEACTIVATE_User", "Vô hiệu hóa tài khoản");
        actionMessages.put("UPDATE_Profile", "Cập nhật hồ sơ cá nhân");
        actionMessages.put("VIEW_Profile", "Xem hồ sơ cá nhân");
        actionMessages.put("UPLOAD_Avatar", "Cập nhật ảnh đại diện");
        actionMessages.put("DELETE_Avatar", "Xóa ảnh đại diện");
        actionMessages.put("UPDATE_Settings", "Cập nhật cài đặt");
        actionMessages.put("UPDATE_Privacy", "Cập nhật cài đặt riêng tư");
        actionMessages.put("UPDATE_Notification", "Cập nhật cài đặt thông báo");

        // HR Management actions (standalone - no entity type needed)
        actionMessages.put("APPROVE_HR", "Phê duyệt tài khoản HR");
        actionMessages.put("APPROVE_HR_MANAGER", "Phê duyệt tài khoản HR Manager");
        actionMessages.put("REJECT_HR", "Từ chối tài khoản HR");
        actionMessages.put("VIEW_HR", "Xem danh sách HR");
        actionMessages.put("VIEW_HR_MANAGER", "Xem danh sách HR Manager");

        // CV actions
        actionMessages.put("UPLOAD_CV", "Tải lên CV");
        actionMessages.put("UPDATE_CV", "Cập nhật CV");
        actionMessages.put("DELETE_CV", "Xóa CV");
        actionMessages.put("VIEW_CV", "Xem CV");
        actionMessages.put("DOWNLOAD_CV", "Tải xuống CV");

        // Company actions
        actionMessages.put("CREATE_Company", "Tạo thông tin công ty");
        actionMessages.put("UPDATE_Company", "Cập nhật thông tin công ty");
        actionMessages.put("VIEW_Company", "Xem thông tin công ty");

        // Report/Analytics
        actionMessages.put("VIEW_Report", "Xem báo cáo");
        actionMessages.put("EXPORT_Report", "Xuất báo cáo");
        actionMessages.put("VIEW_Analytics", "Xem thống kê");

        return actionMessages.get(key);
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
