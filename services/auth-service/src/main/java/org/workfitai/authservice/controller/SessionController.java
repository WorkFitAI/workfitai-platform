package org.workfitai.authservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.workfitai.authservice.dto.response.SessionResponse;
import org.workfitai.authservice.exception.NotFoundException;
import org.workfitai.authservice.model.User;
import org.workfitai.authservice.repository.UserRepository;
import org.workfitai.authservice.service.SessionService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<SessionResponse>> getSessions(
            Authentication authentication,
            @RequestAttribute(value = "sessionId", required = false) String currentSessionId) {

        String username = authentication.getName();
        log.info("Get sessions request for user: {}", username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));

        List<SessionResponse> sessions = sessionService.getUserSessions(user.getId(), currentSessionId);

        return ResponseEntity.ok(sessions);
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Map<String, String>> deleteSession(
            @PathVariable String sessionId,
            Authentication authentication,
            @RequestAttribute(value = "sessionId", required = false) String currentSessionId) {

        String username = authentication.getName();
        log.info("Delete session {} request for user: {}", sessionId, username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));

        sessionService.deleteSession(user.getId(), sessionId, currentSessionId);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Session deleted successfully");

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/all")
    public ResponseEntity<Map<String, String>> deleteAllSessions(
            Authentication authentication,
            @RequestAttribute(value = "sessionId", required = false) String currentSessionId) {

        String username = authentication.getName();
        log.info("Delete all sessions request for user: {}", username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));

        sessionService.deleteAllSessionsExceptCurrent(user.getId(), currentSessionId);

        Map<String, String> response = new HashMap<>();
        response.put("message", "All other sessions deleted successfully");

        return ResponseEntity.ok(response);
    }
}
