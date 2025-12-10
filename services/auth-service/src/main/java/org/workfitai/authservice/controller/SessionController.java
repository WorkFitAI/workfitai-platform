package org.workfitai.authservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.workfitai.authservice.dto.response.SessionResponse;
import org.workfitai.authservice.service.SessionService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/auth/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @GetMapping
    public ResponseEntity<List<SessionResponse>> getSessions(
            Authentication authentication,
            @RequestAttribute(value = "sessionId", required = false) String currentSessionId) {

        String userId = authentication.getName();
        log.info("Get sessions request for user: {}", userId);

        List<SessionResponse> sessions = sessionService.getUserSessions(userId, currentSessionId);

        return ResponseEntity.ok(sessions);
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Map<String, String>> deleteSession(
            @PathVariable String sessionId,
            Authentication authentication,
            @RequestAttribute(value = "sessionId", required = false) String currentSessionId) {

        String userId = authentication.getName();
        log.info("Delete session {} request for user: {}", sessionId, userId);

        sessionService.deleteSession(userId, sessionId, currentSessionId);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Session deleted successfully");

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/all")
    public ResponseEntity<Map<String, String>> deleteAllSessions(
            Authentication authentication,
            @RequestAttribute(value = "sessionId", required = false) String currentSessionId) {

        String userId = authentication.getName();
        log.info("Delete all sessions request for user: {}", userId);

        sessionService.deleteAllSessionsExceptCurrent(userId, currentSessionId);

        Map<String, String> response = new HashMap<>();
        response.put("message", "All other sessions deleted successfully");

        return ResponseEntity.ok(response);
    }
}
