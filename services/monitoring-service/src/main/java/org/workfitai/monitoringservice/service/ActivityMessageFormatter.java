package org.workfitai.monitoringservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

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

    private static final Map<String, String> RESOURCE_TRANSLATIONS = Map.ofEntries(
            Map.entry("jobs",          "tin tuyển dụng"),
            Map.entry("applications",  "hồ sơ ứng tuyển"),
            Map.entry("users",         "người dùng"),
            Map.entry("profile",       "hồ sơ cá nhân"),
            Map.entry("cv",            "CV"),
            Map.entry("company",       "công ty"),
            Map.entry("companies",     "công ty"),
            Map.entry("reports",       "báo cáo"),
            Map.entry("analytics",     "thống kê"),
            Map.entry("dashboard",     "trang tổng quan"),
            Map.entry("notifications", "thông báo"),
            Map.entry("settings",      "cài đặt")
    );

    // ─── Public API ───────────────────────────────────────────────────────────────

    public String formatActivityMessage(String method, String path, String action,
                                         String entityType, String service) {
        if (action != null && entityType != null) {
            String message = formatByAction(action, entityType);
            if (message != null) return message;
        }
        String message = formatByPath(method, path);
        if (message != null) return message;
        return formatGeneric(method, path, service);
    }

    /**
     * Resolve a human-readable message for an action+entityType combination.
     * Package-private so AdminActivityService can call it directly.
     */
    String formatByAction(String action, String entityType) {
        if (action == null) return null;
        String key = (entityType != null && !entityType.isEmpty())
                ? action + "_" + entityType
                : action;
        return auditPatternService.resolveMessage(key).orElse(null);
    }

    public String formatTimestamp(Instant timestamp) {
        if (timestamp == null) return "";
        return FORMATTER.format(timestamp);
    }

    public String formatRelativeTime(Instant timestamp) {
        if (timestamp == null) return "";
        long seconds = Instant.now().getEpochSecond() - timestamp.getEpochSecond();
        if (seconds < 60)     return "Vừa xong";
        if (seconds < 3600)   return (seconds / 60) + " phút trước";
        if (seconds < 86400)  return (seconds / 3600) + " giờ trước";
        if (seconds < 604800) return (seconds / 86400) + " ngày trước";
        return formatTimestamp(timestamp);
    }

    public String getActivityIcon(String action, String entityType) {
        if (action == null) return "📝";
        return switch (action) {
            case "CREATE", "SUBMIT"                          -> "➕";
            case "UPDATE", "EDIT"                            -> "✏️";
            case "DELETE", "WITHDRAW"                        -> "🗑️";
            case "VIEW", "READ", "LIST"                      -> "👁️";
            case "APPROVE"                                   -> "✅";
            case "REJECT"                                    -> "❌";
            case "UPLOAD"                                    -> "📤";
            case "DOWNLOAD"                                  -> "📥";
            case "SEARCH", "FILTER"                          -> "🔍";
            case "LOGIN"                                     -> "🔐";
            case "LOGOUT", "LOGOUT_ALL", "LOGOUT_SESSION"   -> "🚪";
            case "ENABLE", "ENABLE_2FA"                      -> "🔓";
            case "DISABLE", "DISABLE_2FA"                    -> "🔒";
            case "BLOCK"                                     -> "🚫";
            case "UNBLOCK"                                   -> "✔️";
            case "PUBLISH"                                   -> "📢";
            case "UNPUBLISH", "CLOSE"                        -> "📴";
            case "REOPEN"                                    -> "🔄";
            case "EXPORT"                                    -> "💾";
            case "INTERVIEW"                                 -> "👥";
            case "OFFER"                                     -> "💼";
            case "SHORTLIST"                                 -> "⭐";
            case "REVIEW"                                    -> "📊";
            case "DEACTIVATE"                                -> "⏸️";
            case "VERIFY"                                    -> "✔️";
            default                                          -> "📝";
        };
    }

    // ─── Path-based formatting ────────────────────────────────────────────────────

    private String formatByPath(String method, String path) {
        if (path == null) return null;
        String result;
        result = formatAuthPath(method, path);      if (result != null) return result;
        result = formatProfilePath(method, path);   if (result != null) return result;
        result = formatJobPath(method, path);        if (result != null) return result;
        result = formatApplicationPath(method, path); if (result != null) return result;
        result = formatCvPath(method, path);         if (result != null) return result;
        result = formatUserPath(method, path);       if (result != null) return result;
        result = formatMiscPath(method, path);       if (result != null) return result;
        return null;
    }

    private String formatAuthPath(String method, String path) {
        if (path.contains("/login"))                                      return "Đăng nhập vào hệ thống";
        if (path.contains("/logout") && !path.contains("/sessions"))      return "Đăng xuất khỏi hệ thống";
        if (path.contains("/register"))                                   return "Đăng ký tài khoản mới";
        if (path.contains("/forgot-password"))                            return "Yêu cầu đặt lại mật khẩu";
        if (path.contains("/reset-password"))                             return "Đặt lại mật khẩu";
        if (path.contains("/change-password"))                            return "Thay đổi mật khẩu";
        if (path.contains("/verify"))                                     return "Xác thực tài khoản";
        if (path.contains("/enable-2fa"))                                 return "Bật xác thực hai yếu tố";
        if (path.contains("/disable-2fa"))                                return "Tắt xác thực hai yếu tố";
        if (path.contains("/sessions/all") && "DELETE".equals(method))   return "Đăng xuất tất cả phiên làm việc";
        if (path.contains("/sessions") && "GET".equals(method))          return "Xem danh sách phiên đăng nhập";
        if (path.contains("/sessions") && "DELETE".equals(method))       return "Đăng xuất phiên làm việc";
        return null;
    }

    private String formatProfilePath(String method, String path) {
        if (path.contains("/profile") && "GET".equals(method))           return "Xem hồ sơ cá nhân";
        if (path.contains("/profile") && "PUT".equals(method))           return "Cập nhật hồ sơ cá nhân";
        if (path.contains("/avatar") && "POST".equals(method))           return "Tải lên ảnh đại diện";
        if (path.contains("/avatar") && "DELETE".equals(method))         return "Xóa ảnh đại diện";
        if (path.contains("/avatar"))                                     return "Xem ảnh đại diện";
        if (path.contains("/notification-settings"))                      return "Cập nhật cài đặt thông báo";
        if (path.contains("/privacy-settings"))                           return "Cập nhật cài đặt riêng tư";
        if (path.contains("/deactivate"))                                 return "Vô hiệu hóa tài khoản";
        if (path.contains("/delete-request"))                             return "Yêu cầu xóa tài khoản";
        return null;
    }

    private String formatJobPath(String method, String path) {
        if (path.matches(".*/jobs$") && "GET".equals(method))            return "Xem danh sách tin tuyển dụng";
        if (path.matches(".*/jobs$") && "POST".equals(method))           return "Tạo tin tuyển dụng mới";
        if (path.matches(".*/jobs/[^/]+$") && "GET".equals(method))     return "Xem chi tiết tin tuyển dụng";
        if (path.matches(".*/jobs/[^/]+$") && "PUT".equals(method))     return "Cập nhật tin tuyển dụng";
        if (path.matches(".*/jobs/[^/]+$") && "DELETE".equals(method))  return "Xóa tin tuyển dụng";
        if (path.contains("/jobs") && path.contains("/publish"))         return "Đăng tin tuyển dụng";
        if (path.contains("/jobs") && path.contains("/search"))          return "Tìm kiếm tin tuyển dụng";
        return null;
    }

    private String formatApplicationPath(String method, String path) {
        if (path.matches(".*/applications$") && "GET".equals(method))            return "Xem danh sách hồ sơ";
        if (path.matches(".*/applications$") && "POST".equals(method))           return "Nộp hồ sơ ứng tuyển";
        if (path.matches(".*/applications/[^/]+$") && "GET".equals(method))     return "Xem chi tiết hồ sơ";
        if (path.matches(".*/applications/[^/]+$") && "PUT".equals(method))     return "Cập nhật hồ sơ";
        if (path.matches(".*/applications/[^/]+$") && "DELETE".equals(method))  return "Rút hồ sơ ứng tuyển";
        if (path.contains("/applications") && path.contains("/status"))          return "Cập nhật trạng thái hồ sơ";
        if (path.contains("/applications") && path.contains("/review"))          return "Đánh giá hồ sơ";
        return null;
    }

    private String formatCvPath(String method, String path) {
        if (!path.contains("/cv")) return null;
        if (path.contains("/cv/download"))      return "Tải xuống CV";
        if ("POST".equals(method))              return "Tải lên CV";
        if ("PUT".equals(method))               return "Cập nhật CV";
        if ("DELETE".equals(method))            return "Xóa CV";
        return "Xem CV";
    }

    private String formatUserPath(String method, String path) {
        if (path.contains("/approve-hr-manager"))  return "Phê duyệt tài khoản HR Manager";
        if (path.contains("/approve-hr"))           return "Phê duyệt tài khoản HR";
        if (path.contains("/approve"))              return "Phê duyệt tài khoản";
        if (path.contains("/reject"))               return "Từ chối tài khoản";
        if (path.contains("/block"))                return "Khóa tài khoản";
        if (path.contains("/unblock"))              return "Mở khóa tài khoản";
        if (path.contains("/admin/users"))          return "Quản lý người dùng";
        if (path.contains("/admin/hr"))             return "Quản lý HR";
        if (path.contains("/users") && "GET".equals(method))  return "Xem danh sách người dùng";
        if (path.contains("/users") && "POST".equals(method)) return "Tạo người dùng mới";
        return null;
    }

    private String formatMiscPath(String method, String path) {
        if (path.contains("/company") && "GET".equals(method))  return "Xem thông tin công ty";
        if (path.contains("/company") && "PUT".equals(method))  return "Cập nhật thông tin công ty";
        if (path.contains("/report") || path.contains("/analytics")) return "Xem báo cáo thống kê";
        if (path.contains("/dashboard"))                         return "Xem trang tổng quan";
        if (path.contains("/activity"))                          return "Xem hoạt động người dùng";
        return null;
    }

    // ─── Generic fallback ─────────────────────────────────────────────────────────

    private String formatGeneric(String method, String path, String service) {
        String action = switch (method != null ? method : "") {
            case "GET"           -> "Xem";
            case "POST"          -> "Tạo mới";
            case "PUT", "PATCH"  -> "Cập nhật";
            case "DELETE"        -> "Xóa";
            default              -> "Thao tác";
        };
        return String.format("%s %s", action, extractResourceFromPath(path));
    }

    private String extractResourceFromPath(String path) {
        if (path == null || path.isEmpty()) return "tài nguyên";
        String cleanPath = path.split("\\?")[0];
        String[] segments = cleanPath.split("/");
        for (int i = segments.length - 1; i >= 0; i--) {
            String segment = segments[i];
            if (segment.matches("[a-f0-9-]{36}|\\d+")) continue;
            if (segment.matches("api|v1|v2")) continue;
            if (!segment.isEmpty()) return translateResourceName(segment);
        }
        return "tài nguyên";
    }

    private String translateResourceName(String resource) {
        return RESOURCE_TRANSLATIONS.getOrDefault(resource.toLowerCase(), resource);
    }
}
