package org.workfitai.authservice.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.workfitai.authservice.dto.response.SessionResponse;
import org.workfitai.authservice.exception.BadRequestException;
import org.workfitai.authservice.exception.NotFoundException;
import org.workfitai.authservice.model.UserSession;
import org.workfitai.authservice.repository.UserSessionRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final UserSessionRepository sessionRepository;
    private final GeoLocationService geoLocationService;

    @Value("${app.session.max-sessions-per-user:5}")
    private int maxSessionsPerUser;

    public List<SessionResponse> getUserSessions(String userId, String currentSessionId) {
        List<UserSession> sessions = sessionRepository.findByUserIdOrderByCreatedAtDesc(userId);

        return sessions.stream()
                .map(session -> mapToResponse(session, currentSessionId))
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteSession(String userId, String sessionId, String currentSessionId) {
        if (sessionId.equals(currentSessionId)) {
            throw new BadRequestException("Cannot delete current session. Use logout instead");
        }

        UserSession session = sessionRepository.findByUserIdAndSessionId(userId, sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));

        sessionRepository.delete(session);
        log.info("Session {} deleted for user {}", sessionId, userId);
    }

    @Transactional
    public void deleteAllSessionsExceptCurrent(String userId, String currentSessionId) {
        List<UserSession> sessions = sessionRepository.findByUserIdAndSessionIdNot(userId, currentSessionId);

        if (sessions.isEmpty()) {
            throw new BadRequestException("No other sessions to delete");
        }

        sessionRepository.deleteAll(sessions);
        log.info("Deleted {} sessions for user {}", sessions.size(), userId);
    }

    @Transactional
    public UserSession createSession(String userId, String refreshTokenHash, Long expirationMs,
            HttpServletRequest request) {
        // Check session limit
        long sessionCount = sessionRepository.countByUserId(userId);
        if (sessionCount >= maxSessionsPerUser) {
            // Delete oldest session
            List<UserSession> sessions = sessionRepository.findByUserIdOrderByCreatedAtDesc(userId);
            if (!sessions.isEmpty()) {
                UserSession oldestSession = sessions.get(sessions.size() - 1);
                sessionRepository.delete(oldestSession);
                log.info("Deleted oldest session for user {} due to session limit", userId);
            }
        }

        String sessionId = UUID.randomUUID().toString();
        String ipAddress = getClientIpAddress(request);
        String userAgent = getUserAgent(request);
        UserSession.Location location = geoLocationService.getLocation(ipAddress);

        UserSession session = UserSession.builder()
                .userId(userId)
                .sessionId(sessionId)
                .refreshTokenHash(refreshTokenHash)
                .deviceId(generateDeviceId(userAgent, ipAddress))
                .deviceName(extractDeviceName(userAgent))
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .location(location)
                .createdAt(LocalDateTime.now())
                .lastActivityAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusSeconds(expirationMs / 1000))
                .build();

        return sessionRepository.save(session);
    }

    @Transactional
    public void updateSessionActivity(String sessionId) {
        sessionRepository.findBySessionId(sessionId).ifPresent(session -> {
            session.setLastActivityAt(LocalDateTime.now());
            sessionRepository.save(session);
        });
    }

    private SessionResponse mapToResponse(UserSession session, String currentSessionId) {
        SessionResponse.LocationData locationData = null;
        if (session.getLocation() != null) {
            locationData = SessionResponse.LocationData.builder()
                    .country(session.getLocation().getCountry())
                    .city(session.getLocation().getCity())
                    .region(session.getLocation().getRegion())
                    .build();
        }

        return SessionResponse.builder()
                .sessionId(session.getSessionId())
                .deviceId(session.getDeviceId())
                .deviceName(session.getDeviceName())
                .ipAddress(session.getIpAddress())
                .location(locationData)
                .createdAt(session.getCreatedAt())
                .lastActivityAt(session.getLastActivityAt())
                .expiresAt(session.getExpiresAt())
                .current(session.getSessionId().equals(currentSessionId))
                .build();
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String remoteAddr = request.getRemoteAddr();
        return remoteAddr != null ? remoteAddr : "0.0.0.0";
    }

    private String getUserAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        return userAgent != null ? userAgent : "Unknown";
    }

    private String generateDeviceId(String userAgent, String ipAddress) {
        return UUID.nameUUIDFromBytes((userAgent + ipAddress).getBytes()).toString();
    }

    private String extractDeviceName(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return "Unknown Device";
        }

        // Simple device detection
        if (userAgent.contains("Mobile")) {
            if (userAgent.contains("iPhone"))
                return "iPhone";
            if (userAgent.contains("iPad"))
                return "iPad";
            if (userAgent.contains("Android"))
                return "Android Phone";
            return "Mobile Device";
        }

        if (userAgent.contains("Windows"))
            return "Windows PC";
        if (userAgent.contains("Mac"))
            return "Mac";
        if (userAgent.contains("Linux"))
            return "Linux PC";

        // Browser detection
        if (userAgent.contains("Chrome"))
            return "Chrome Browser";
        if (userAgent.contains("Firefox"))
            return "Firefox Browser";
        if (userAgent.contains("Safari"))
            return "Safari Browser";
        if (userAgent.contains("Edge"))
            return "Edge Browser";

        return "Unknown Device";
    }
}
