package org.workfitai.authservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.workfitai.authservice.dto.response.SessionResponse;
import org.workfitai.authservice.exception.BadRequestException;
import org.workfitai.authservice.exception.NotFoundException;
import org.workfitai.authservice.model.UserSession;
import org.workfitai.authservice.repository.UserSessionRepository;

import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock UserSessionRepository sessionRepository;
    @Mock GeoLocationService geoLocationService;
    @Mock HttpServletRequest httpRequest;

    @InjectMocks SessionService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "maxSessionsPerUser", 5);
    }

    private UserSession session(String sessionId, String userId) {
        return UserSession.builder()
                .sessionId(sessionId)
                .userId(userId)
                .deviceName("Chrome on macOS")
                .ipAddress("127.0.0.1")
                .userAgent("Mozilla/5.0")
                .createdAt(LocalDateTime.now().minusHours(1))
                .lastActivityAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
    }

    // ─── getUserSessions ──────────────────────────────────────────────────────

    @Test
    void getUserSessions_returnsAllSessionsForUser() {
        when(sessionRepository.findByUserIdOrderByCreatedAtDesc("user-1"))
                .thenReturn(List.of(session("sid-1", "user-1"), session("sid-2", "user-1")));

        List<SessionResponse> result = service.getUserSessions("user-1", "sid-1");

        assertThat(result).hasSize(2);
    }

    @Test
    void getUserSessions_marksCurrentSessionCorrectly() {
        when(sessionRepository.findByUserIdOrderByCreatedAtDesc("user-1"))
                .thenReturn(List.of(session("sid-current", "user-1"), session("sid-other", "user-1")));

        List<SessionResponse> result = service.getUserSessions("user-1", "sid-current");

        assertThat(result).anyMatch(s -> s.getSessionId().equals("sid-current") && Boolean.TRUE.equals(s.getCurrent()));
        assertThat(result).anyMatch(s -> s.getSessionId().equals("sid-other") && !Boolean.TRUE.equals(s.getCurrent()));
    }

    @Test
    void getUserSessions_returnsEmpty_whenNoSessions() {
        when(sessionRepository.findByUserIdOrderByCreatedAtDesc("user-1")).thenReturn(List.of());

        assertThat(service.getUserSessions("user-1", "sid")).isEmpty();
    }

    // ─── deleteSession ────────────────────────────────────────────────────────

    @Test
    void deleteSession_deletesSessionById() {
        UserSession sess = session("sid-2", "user-1");
        when(sessionRepository.findByUserIdAndSessionId("user-1", "sid-2"))
                .thenReturn(Optional.of(sess));

        service.deleteSession("user-1", "sid-2", "sid-current");

        verify(sessionRepository).delete(sess);
    }

    @Test
    void deleteSession_throwsBadRequest_whenDeletingCurrentSession() {
        assertThatThrownBy(() -> service.deleteSession("user-1", "sid-current", "sid-current"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot delete current session");
    }

    @Test
    void deleteSession_throwsNotFound_whenSessionNotFound() {
        when(sessionRepository.findByUserIdAndSessionId("user-1", "sid-ghost"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteSession("user-1", "sid-ghost", "sid-current"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Session not found");
    }

    // ─── deleteAllSessionsExceptCurrent ───────────────────────────────────────

    @Test
    void deleteAllSessionsExceptCurrent_deletesOtherSessions() {
        List<UserSession> others = List.of(session("sid-2", "user-1"), session("sid-3", "user-1"));
        when(sessionRepository.findByUserIdAndSessionIdNot("user-1", "sid-current")).thenReturn(others);

        service.deleteAllSessionsExceptCurrent("user-1", "sid-current");

        verify(sessionRepository).deleteAll(others);
    }

    @Test
    void deleteAllSessionsExceptCurrent_throwsBadRequest_whenNoOtherSessions() {
        when(sessionRepository.findByUserIdAndSessionIdNot("user-1", "sid-current"))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.deleteAllSessionsExceptCurrent("user-1", "sid-current"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No other sessions");
    }

    // ─── createSession ────────────────────────────────────────────────────────

    @Test
    void createSession_savesAndReturnsSession_belowLimit() {
        when(sessionRepository.countByUserId("user-1")).thenReturn(2L);
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(httpRequest.getHeader("User-Agent")).thenReturn("Chrome/120");
        when(geoLocationService.getLocation(anyString())).thenReturn(null);
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserSession result = service.createSession(
                "user-1", "rt-hash", 604_800_000L, httpRequest, null, null);

        assertThat(result.getUserId()).isEqualTo("user-1");
        assertThat(result.getRefreshTokenHash()).isEqualTo("rt-hash");
        verify(sessionRepository).save(any(UserSession.class));
    }

    @Test
    void createSession_evictsOldestSession_whenAtLimit() {
        when(sessionRepository.countByUserId("user-1")).thenReturn(5L); // at max
        List<UserSession> sessions = List.of(
                session("old-1", "user-1"), session("old-2", "user-1"),
                session("old-3", "user-1"), session("old-4", "user-1"),
                session("oldest", "user-1"));
        when(sessionRepository.findByUserIdOrderByCreatedAtDesc("user-1")).thenReturn(sessions);
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(httpRequest.getHeader("User-Agent")).thenReturn("Chrome/120");
        when(geoLocationService.getLocation(anyString())).thenReturn(null);
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createSession("user-1", "rt-hash", 604_800_000L, httpRequest, null, null);

        verify(sessionRepository).delete(sessions.get(4)); // oldest = last
    }

    @Test
    void createSession_usesCoordinatesOverIpGeolocation_whenProvided() {
        when(sessionRepository.countByUserId("user-1")).thenReturn(0L);
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(httpRequest.getHeader("User-Agent")).thenReturn("Chrome/120");
        when(geoLocationService.getLocationFromCoordinates(10.0, 106.0)).thenReturn(null);
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createSession("user-1", "rt-hash", 3_600_000L, httpRequest, 10.0, 106.0);

        verify(geoLocationService).getLocationFromCoordinates(10.0, 106.0);
    }

    @Test
    void createSession_usesForwardedForAndMapsLocationIntoResponse() {
        UserSession.Location location = UserSession.Location.builder()
                .country("VN").region("HCMC").city("Saigon").build();
        when(sessionRepository.countByUserId("user-1")).thenReturn(0L);
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1, 10.0.0.2");
        when(httpRequest.getHeader("User-Agent")).thenReturn("PostmanRuntime/7.0");
        when(geoLocationService.getLocation("10.0.0.1")).thenReturn(location);
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserSession session = service.createSession(
                "user-1", "rt-hash", 3_600_000L, httpRequest, null, null);
        when(sessionRepository.findByUserIdOrderByCreatedAtDesc("user-1")).thenReturn(List.of(session));

        List<SessionResponse> responses = service.getUserSessions("user-1", session.getSessionId());

        assertThat(session.getIpAddress()).isEqualTo("10.0.0.1");
        assertThat(session.getDeviceName()).isEqualTo("Postman");
        assertThat(responses.get(0).getLocation().getCountry()).isEqualTo("VN");
        assertThat(responses.get(0).getLocation().getRegion()).isEqualTo("HCMC");
        assertThat(responses.get(0).getLocation().getCity()).isEqualTo("Saigon");
    }

    @Test
    void createSession_fallsBackWhenRemoteAddrAndUserAgentMissing() {
        when(sessionRepository.countByUserId("user-1")).thenReturn(0L);
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("");
        when(httpRequest.getRemoteAddr()).thenReturn(null);
        when(httpRequest.getHeader("User-Agent")).thenReturn(null);
        when(geoLocationService.getLocation("0.0.0.0")).thenReturn(null);
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserSession result = service.createSession(
                "user-1", "rt-hash", 3_600_000L, httpRequest, null, null);

        assertThat(result.getIpAddress()).isEqualTo("0.0.0.0");
        assertThat(result.getUserAgent()).isEqualTo("Unknown");
        assertThat(result.getDeviceName()).isEqualTo("Unknown Device");
    }

    @Test
    void createSession_detectsCommonMobileAndDesktopDeviceNames() {
        assertDeviceName("Mozilla/5.0 Mobile iPhone", "iPhone");
        assertDeviceName("Mozilla/5.0 Mobile iPad", "iPad");
        assertDeviceName("Mozilla/5.0 Mobile Android", "Android Phone");
        assertDeviceName("Mozilla/5.0 Mobile", "Mobile Device");
        assertDeviceName("Mozilla/5.0 Windows NT 10.0", "Windows PC");
        assertDeviceName("Mozilla/5.0 Macintosh", "Mac");
        assertDeviceName("Mozilla/5.0 Linux x86_64", "Linux PC");
        assertDeviceName("Mozilla/5.0 Firefox/120", "Firefox Browser");
        assertDeviceName("Mozilla/5.0 Safari/605", "Safari Browser");
        assertDeviceName("Mozilla/5.0 Edge/120", "Edge Browser");
        assertDeviceName("CustomAgent/1.0", "Unknown Device");
    }

    // ─── updateSessionActivity ────────────────────────────────────────────────

    @Test
    void updateSessionActivity_updatesLastActivityAt_whenSessionExists() {
        UserSession sess = session("sid-1", "user-1");
        when(sessionRepository.findBySessionId("sid-1")).thenReturn(Optional.of(sess));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateSessionActivity("sid-1");

        verify(sessionRepository).save(sess);
    }

    @Test
    void updateSessionActivity_isNoOp_whenSessionNotFound() {
        when(sessionRepository.findBySessionId("sid-ghost")).thenReturn(Optional.empty());

        service.updateSessionActivity("sid-ghost"); // must not throw
    }

    private void assertDeviceName(String userAgent, String expectedDeviceName) {
        reset(sessionRepository, geoLocationService, httpRequest);
        when(sessionRepository.countByUserId("user-1")).thenReturn(0L);
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(httpRequest.getHeader("User-Agent")).thenReturn(userAgent);
        when(geoLocationService.getLocation("127.0.0.1")).thenReturn(null);
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserSession result = service.createSession(
                "user-1", "rt-hash", 3_600_000L, httpRequest, null, null);

        assertThat(result.getDeviceName()).isEqualTo(expectedDeviceName);
    }
}
