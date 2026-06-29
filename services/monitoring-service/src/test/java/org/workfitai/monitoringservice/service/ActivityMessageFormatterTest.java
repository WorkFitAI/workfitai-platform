package org.workfitai.monitoringservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityMessageFormatterTest {

    @Mock
    private AuditPatternService auditPatternService;

    private ActivityMessageFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new ActivityMessageFormatter(auditPatternService);
        lenient().when(auditPatternService.resolveMessage(anyString())).thenReturn(Optional.empty());
    }

    private String byPath(String method, String path) {
        return formatter.formatActivityMessage(method, path, null, null, "svc");
    }

    // ─── formatActivityMessage / formatByAction ────────────────────────────────────

    @Test
    void formatActivityMessage_resolvesViaAuditPattern_whenActionAndEntityTypePresent() {
        when(auditPatternService.resolveMessage("CREATE_Job")).thenReturn(Optional.of("{actor} created a job"));

        String result = formatter.formatActivityMessage("POST", "/jobs", "CREATE", "Job", "job-service");

        assertThat(result).isEqualTo("{actor} created a job");
    }

    @Test
    void formatActivityMessage_fallsBackToPath_whenPatternMissing() {
        String result = formatter.formatActivityMessage("POST", "/auth/login", null, null, "auth-service");

        assertThat(result).isEqualTo("Đăng nhập vào hệ thống");
    }

    @Test
    void formatActivityMessage_fallsBackToGeneric_whenNoActionAndNoPathMatch() {
        String result = formatter.formatActivityMessage("GET", "/unknown/resource", null, null, "some-service");

        assertThat(result).isEqualTo("Xem resource");
    }

    @Test
    void formatByAction_returnsNull_whenActionIsNull() {
        assertThat(formatter.formatByAction(null, "Job")).isNull();
    }

    @Test
    void formatByAction_usesActionOnly_whenEntityTypeBlank() {
        when(auditPatternService.resolveMessage("LOGIN")).thenReturn(Optional.of("Logged in"));

        assertThat(formatter.formatByAction("LOGIN", "")).isEqualTo("Logged in");
    }

    @Test
    void formatByAction_usesActionOnly_whenEntityTypeNull() {
        when(auditPatternService.resolveMessage("LOGIN")).thenReturn(Optional.of("Logged in"));

        assertThat(formatter.formatByAction("LOGIN", null)).isEqualTo("Logged in");
    }

    // ─── formatAuthPath ─────────────────────────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({
            "POST,/auth/login,Đăng nhập vào hệ thống",
            "POST,/auth/logout,Đăng xuất khỏi hệ thống",
            "POST,/auth/register,Đăng ký tài khoản mới",
            "POST,/auth/forgot-password,Yêu cầu đặt lại mật khẩu",
            "POST,/auth/reset-password,Đặt lại mật khẩu",
            "POST,/auth/change-password,Thay đổi mật khẩu",
            "GET,/auth/verify,Xác thực tài khoản",
            "POST,/auth/enable-2fa,Bật xác thực hai yếu tố",
            "POST,/auth/disable-2fa,Tắt xác thực hai yếu tố",
    })
    void formatAuthPath_matchesEachPattern(String method, String path, String expected) {
        assertThat(byPath(method, path)).isEqualTo(expected);
    }

    @Test
    void formatAuthPath_logoutWithSessions_doesNotMatchPlainLogout() {
        assertThat(byPath("DELETE", "/auth/logout/sessions/all"))
                .isEqualTo("Đăng xuất tất cả phiên làm việc");
    }

    @Test
    void formatAuthPath_sessionsAllDelete_returnsLogoutAllSessions() {
        assertThat(byPath("DELETE", "/auth/sessions/all"))
                .isEqualTo("Đăng xuất tất cả phiên làm việc");
    }

    @Test
    void formatAuthPath_sessionsGet_returnsViewSessions() {
        assertThat(byPath("GET", "/auth/sessions"))
                .isEqualTo("Xem danh sách phiên đăng nhập");
    }

    @Test
    void formatAuthPath_sessionsDelete_returnsLogoutSession() {
        assertThat(byPath("DELETE", "/auth/sessions/abc"))
                .isEqualTo("Đăng xuất phiên làm việc");
    }

    // ─── formatProfilePath ──────────────────────────────────────────────────────────

    @Test
    void formatProfilePath_getProfile_returnsViewProfile() {
        assertThat(byPath("GET", "/user/profile")).isEqualTo("Xem hồ sơ cá nhân");
    }

    @Test
    void formatProfilePath_putProfile_returnsUpdateProfile() {
        assertThat(byPath("PUT", "/user/profile")).isEqualTo("Cập nhật hồ sơ cá nhân");
    }

    @Test
    void formatProfilePath_postAvatar_returnsUploadAvatar() {
        assertThat(byPath("POST", "/user/avatar")).isEqualTo("Tải lên ảnh đại diện");
    }

    @Test
    void formatProfilePath_deleteAvatar_returnsDeleteAvatar() {
        assertThat(byPath("DELETE", "/user/avatar")).isEqualTo("Xóa ảnh đại diện");
    }

    @Test
    void formatProfilePath_getAvatar_returnsViewAvatar() {
        assertThat(byPath("GET", "/user/avatar")).isEqualTo("Xem ảnh đại diện");
    }

    @Test
    void formatProfilePath_notificationSettings_returnsUpdateNotificationSettings() {
        assertThat(byPath("PUT", "/user/notification-settings")).isEqualTo("Cập nhật cài đặt thông báo");
    }

    @Test
    void formatProfilePath_privacySettings_returnsUpdatePrivacySettings() {
        assertThat(byPath("PUT", "/user/privacy-settings")).isEqualTo("Cập nhật cài đặt riêng tư");
    }

    @Test
    void formatProfilePath_deactivate_returnsDeactivateAccount() {
        assertThat(byPath("POST", "/user/deactivate")).isEqualTo("Vô hiệu hóa tài khoản");
    }

    @Test
    void formatProfilePath_deleteRequest_returnsDeleteAccountRequest() {
        assertThat(byPath("POST", "/user/delete-request")).isEqualTo("Yêu cầu xóa tài khoản");
    }

    // ─── formatJobPath ──────────────────────────────────────────────────────────────

    @Test
    void formatJobPath_getJobsList_returnsViewJobList() {
        assertThat(byPath("GET", "/jobs")).isEqualTo("Xem danh sách tin tuyển dụng");
    }

    @Test
    void formatJobPath_postJobsList_returnsCreateJob() {
        assertThat(byPath("POST", "/jobs")).isEqualTo("Tạo tin tuyển dụng mới");
    }

    @Test
    void formatJobPath_getJobDetail_returnsViewJobDetail() {
        assertThat(byPath("GET", "/jobs/abc-123")).isEqualTo("Xem chi tiết tin tuyển dụng");
    }

    @Test
    void formatJobPath_putJobDetail_returnsUpdateJob() {
        assertThat(byPath("PUT", "/jobs/abc-123")).isEqualTo("Cập nhật tin tuyển dụng");
    }

    @Test
    void formatJobPath_deleteJobDetail_returnsDeleteJob() {
        assertThat(byPath("DELETE", "/jobs/abc-123")).isEqualTo("Xóa tin tuyển dụng");
    }

    @Test
    void formatJobPath_publish_returnsPublishJob() {
        assertThat(byPath("POST", "/jobs/abc/publish")).isEqualTo("Đăng tin tuyển dụng");
    }

    @Test
    void formatJobPath_search_returnsSearchJob() {
        assertThat(byPath("GET", "/jobs/category/search")).isEqualTo("Tìm kiếm tin tuyển dụng");
    }

    // ─── formatApplicationPath ──────────────────────────────────────────────────────

    @Test
    void formatApplicationPath_getList_returnsViewApplicationList() {
        assertThat(byPath("GET", "/applications")).isEqualTo("Xem danh sách hồ sơ");
    }

    @Test
    void formatApplicationPath_postList_returnsSubmitApplication() {
        assertThat(byPath("POST", "/applications")).isEqualTo("Nộp hồ sơ ứng tuyển");
    }

    @Test
    void formatApplicationPath_getDetail_returnsViewApplicationDetail() {
        assertThat(byPath("GET", "/applications/abc")).isEqualTo("Xem chi tiết hồ sơ");
    }

    @Test
    void formatApplicationPath_putDetail_returnsUpdateApplication() {
        assertThat(byPath("PUT", "/applications/abc")).isEqualTo("Cập nhật hồ sơ");
    }

    @Test
    void formatApplicationPath_deleteDetail_returnsWithdrawApplication() {
        assertThat(byPath("DELETE", "/applications/abc")).isEqualTo("Rút hồ sơ ứng tuyển");
    }

    @Test
    void formatApplicationPath_status_returnsUpdateApplicationStatus() {
        assertThat(byPath("PUT", "/applications/abc/status")).isEqualTo("Cập nhật trạng thái hồ sơ");
    }

    @Test
    void formatApplicationPath_review_returnsReviewApplication() {
        assertThat(byPath("POST", "/applications/abc/review")).isEqualTo("Đánh giá hồ sơ");
    }

    // ─── formatCvPath ───────────────────────────────────────────────────────────────

    @Test
    void formatCvPath_download_returnsDownloadCv() {
        assertThat(byPath("GET", "/cv/download")).isEqualTo("Tải xuống CV");
    }

    @Test
    void formatCvPath_post_returnsUploadCv() {
        assertThat(byPath("POST", "/cv")).isEqualTo("Tải lên CV");
    }

    @Test
    void formatCvPath_put_returnsUpdateCv() {
        assertThat(byPath("PUT", "/cv")).isEqualTo("Cập nhật CV");
    }

    @Test
    void formatCvPath_delete_returnsDeleteCv() {
        assertThat(byPath("DELETE", "/cv")).isEqualTo("Xóa CV");
    }

    @Test
    void formatCvPath_get_returnsViewCv() {
        assertThat(byPath("GET", "/cv")).isEqualTo("Xem CV");
    }

    // ─── formatUserPath ─────────────────────────────────────────────────────────────

    @Test
    void formatUserPath_approveHrManager_returnsApproveHrManager() {
        assertThat(byPath("POST", "/users/approve-hr-manager")).isEqualTo("Phê duyệt tài khoản HR Manager");
    }

    @Test
    void formatUserPath_approveHr_returnsApproveHr() {
        assertThat(byPath("POST", "/users/approve-hr")).isEqualTo("Phê duyệt tài khoản HR");
    }

    @Test
    void formatUserPath_approve_returnsApproveAccount() {
        assertThat(byPath("POST", "/users/123/approve")).isEqualTo("Phê duyệt tài khoản");
    }

    @Test
    void formatUserPath_reject_returnsRejectAccount() {
        assertThat(byPath("POST", "/users/123/reject")).isEqualTo("Từ chối tài khoản");
    }

    @Test
    void formatUserPath_block_returnsBlockAccount() {
        assertThat(byPath("POST", "/users/123/block")).isEqualTo("Khóa tài khoản");
    }

    @Test
    void formatUserPath_unblock_returnsUnblockAccount() {
        assertThat(byPath("POST", "/users/123/unblock")).isEqualTo("Mở khóa tài khoản");
    }

    @Test
    void formatUserPath_adminUsers_returnsManageUsers() {
        assertThat(byPath("GET", "/admin/users")).isEqualTo("Quản lý người dùng");
    }

    @Test
    void formatUserPath_adminHr_returnsManageHr() {
        assertThat(byPath("GET", "/admin/hr")).isEqualTo("Quản lý HR");
    }

    @Test
    void formatUserPath_getUsers_returnsViewUserList() {
        assertThat(byPath("GET", "/users")).isEqualTo("Xem danh sách người dùng");
    }

    @Test
    void formatUserPath_postUsers_returnsCreateUser() {
        assertThat(byPath("POST", "/users")).isEqualTo("Tạo người dùng mới");
    }

    // ─── formatMiscPath ─────────────────────────────────────────────────────────────

    @Test
    void formatMiscPath_getCompany_returnsViewCompany() {
        assertThat(byPath("GET", "/company")).isEqualTo("Xem thông tin công ty");
    }

    @Test
    void formatMiscPath_putCompany_returnsUpdateCompany() {
        assertThat(byPath("PUT", "/company")).isEqualTo("Cập nhật thông tin công ty");
    }

    @Test
    void formatMiscPath_report_returnsViewReport() {
        assertThat(byPath("GET", "/report")).isEqualTo("Xem báo cáo thống kê");
    }

    @Test
    void formatMiscPath_analytics_returnsViewReport() {
        assertThat(byPath("GET", "/analytics")).isEqualTo("Xem báo cáo thống kê");
    }

    @Test
    void formatMiscPath_dashboard_returnsViewDashboard() {
        assertThat(byPath("GET", "/dashboard")).isEqualTo("Xem trang tổng quan");
    }

    @Test
    void formatMiscPath_activity_returnsViewActivity() {
        assertThat(byPath("GET", "/activity")).isEqualTo("Xem hoạt động người dùng");
    }

    @Test
    void formatMiscPath_unknownPath_fallsThroughToGeneric() {
        assertThat(byPath("GET", "/totally/unmatched/path")).isNotNull();
    }

    // ─── formatGeneric / extractResourceFromPath ───────────────────────────────────

    @Test
    void formatGeneric_get_returnsXem() {
        assertThat(byPath("GET", "/widgets")).isEqualTo("Xem widgets");
    }

    @Test
    void formatGeneric_post_returnsTaoMoi() {
        assertThat(byPath("POST", "/widgets")).isEqualTo("Tạo mới widgets");
    }

    @Test
    void formatGeneric_put_returnsCapNhat() {
        assertThat(byPath("PUT", "/widgets")).isEqualTo("Cập nhật widgets");
    }

    @Test
    void formatGeneric_patch_returnsCapNhat() {
        assertThat(byPath("PATCH", "/widgets")).isEqualTo("Cập nhật widgets");
    }

    @Test
    void formatGeneric_delete_returnsXoa() {
        assertThat(byPath("DELETE", "/widgets")).isEqualTo("Xóa widgets");
    }

    @Test
    void formatGeneric_unknownMethod_returnsThaoTac() {
        assertThat(byPath("PURGE", "/widgets")).isEqualTo("Thao tác widgets");
    }

    @Test
    void formatGeneric_nullMethod_returnsThaoTac() {
        assertThat(byPath(null, "/widgets")).isEqualTo("Thao tác widgets");
    }

    @Test
    void extractResourceFromPath_stripsQueryString() {
        assertThat(byPath("GET", "/widgets?foo=bar")).isEqualTo("Xem widgets");
    }

    @Test
    void extractResourceFromPath_skipsUuidAndApiVersionSegments() {
        assertThat(byPath("GET", "/api/v1/widgets/123e4567-e89b-12d3-a456-426614174000"))
                .isEqualTo("Xem widgets");
    }

    @Test
    void extractResourceFromPath_skipsNumericIdSegment() {
        assertThat(byPath("GET", "/widgets/12345")).isEqualTo("Xem widgets");
    }

    @Test
    void extractResourceFromPath_translatesKnownResourceName() {
        assertThat(byPath("GET", "/notifications")).isEqualTo("Xem thông báo");
    }

    @Test
    void extractResourceFromPath_returnsDefaultResource_whenPathEmpty() {
        assertThat(byPath("GET", "")).isEqualTo("Xem tài nguyên");
    }

    @Test
    void extractResourceFromPath_returnsDefaultResource_whenAllSegmentsFiltered() {
        assertThat(byPath("GET", "/api/v1/123")).isEqualTo("Xem tài nguyên");
    }

    // ─── formatTimestamp / formatRelativeTime ──────────────────────────────────────

    @Test
    void formatTimestamp_returnsEmptyString_whenNull() {
        assertThat(formatter.formatTimestamp(null)).isEmpty();
    }

    @Test
    void formatTimestamp_formatsNonNullInstant() {
        Instant timestamp = Instant.parse("2024-01-15T10:30:00Z");

        assertThat(formatter.formatTimestamp(timestamp)).isNotBlank();
    }

    @Test
    void formatRelativeTime_returnsEmptyString_whenNull() {
        assertThat(formatter.formatRelativeTime(null)).isEmpty();
    }

    @Test
    void formatRelativeTime_returnsVuaXong_forRecentTimestamp() {
        Instant timestamp = Instant.now().minus(10, ChronoUnit.SECONDS);

        assertThat(formatter.formatRelativeTime(timestamp)).isEqualTo("Vừa xong");
    }

    @Test
    void formatRelativeTime_returnsMinutesAgo_forTimestampWithinHour() {
        Instant timestamp = Instant.now().minus(5, ChronoUnit.MINUTES);

        assertThat(formatter.formatRelativeTime(timestamp)).contains("phút trước");
    }

    @Test
    void formatRelativeTime_returnsHoursAgo_forTimestampWithinDay() {
        Instant timestamp = Instant.now().minus(3, ChronoUnit.HOURS);

        assertThat(formatter.formatRelativeTime(timestamp)).contains("giờ trước");
    }

    @Test
    void formatRelativeTime_returnsDaysAgo_forTimestampWithinWeek() {
        Instant timestamp = Instant.now().minus(2, ChronoUnit.DAYS);

        assertThat(formatter.formatRelativeTime(timestamp)).contains("ngày trước");
    }

    @Test
    void formatRelativeTime_fallsBackToFormattedTimestamp_forOldTimestamp() {
        Instant timestamp = Instant.now().minus(30, ChronoUnit.DAYS);

        assertThat(formatter.formatRelativeTime(timestamp)).isNotBlank();
        assertThat(formatter.formatRelativeTime(timestamp)).doesNotContain("trước");
    }

    // ─── getActivityIcon ────────────────────────────────────────────────────────────

    @Test
    void getActivityIcon_returnsDefaultIcon_whenActionNull() {
        assertThat(formatter.getActivityIcon(null, "Job")).isEqualTo("📝");
    }

    @ParameterizedTest
    @CsvSource({
            "CREATE,➕", "SUBMIT,➕",
            "UPDATE,✏️", "EDIT,✏️",
            "DELETE,🗑️", "WITHDRAW,🗑️",
            "VIEW,👁️", "READ,👁️", "LIST,👁️",
            "APPROVE,✅",
            "REJECT,❌",
            "UPLOAD,📤",
            "DOWNLOAD,📥",
            "SEARCH,🔍", "FILTER,🔍",
            "LOGIN,🔐",
            "LOGOUT,🚪", "LOGOUT_ALL,🚪", "LOGOUT_SESSION,🚪",
            "ENABLE,🔓", "ENABLE_2FA,🔓",
            "DISABLE,🔒", "DISABLE_2FA,🔒",
            "BLOCK,🚫",
            "UNBLOCK,✔️",
            "PUBLISH,📢",
            "UNPUBLISH,📴", "CLOSE,📴",
            "REOPEN,🔄",
            "EXPORT,💾",
            "INTERVIEW,👥",
            "OFFER,💼",
            "SHORTLIST,⭐",
            "REVIEW,📊",
            "DEACTIVATE,⏸️",
            "VERIFY,✔️",
    })
    void getActivityIcon_mapsEachKnownAction(String action, String expectedIcon) {
        assertThat(formatter.getActivityIcon(action, "Job")).isEqualTo(expectedIcon);
    }

    @Test
    void getActivityIcon_returnsDefault_forUnknownAction() {
        assertThat(formatter.getActivityIcon("SOME_UNKNOWN_ACTION", null)).isEqualTo("📝");
    }
}
