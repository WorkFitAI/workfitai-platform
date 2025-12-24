package org.workfitai.apigateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Fallback Controller
 * 
 * Provides graceful degradation responses when downstream services are
 * unavailable.
 * Returns user-friendly error messages instead of raw 500/503 errors.
 * 
 * Each fallback includes:
 * - Service name that failed
 * - Timestamp
 * - User-friendly message
 * - Suggested action
 */
@RestController
@RequestMapping("/fallback")
@Slf4j
public class FallbackController {

    /**
     * Generic fallback for any service
     */
    @GetMapping("/{service}")
    public Mono<ResponseEntity<Map<String, Object>>> genericFallback(
            @PathVariable String service,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {

        log.warn("🔴 Circuit breaker triggered for service: {} [requestId={}]", service, requestId);

        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status", 503,
                        "error", "Service Unavailable",
                        "message",
                        String.format("The %s service is temporarily unavailable. Please try again later.", service),
                        "service", service,
                        "timestamp", LocalDateTime.now().toString(),
                        "requestId", requestId != null ? requestId : "N/A",
                        "suggestion", "If this problem persists, please contact support.")));
    }

    /**
     * Auth Service Fallback
     */
    @PostMapping("/auth/login")
    public Mono<ResponseEntity<Map<String, Object>>> authLoginFallback() {
        log.error("🔴 Auth service login fallback triggered");
        return buildFallbackResponse(
                "auth",
                "Login service is currently unavailable",
                "Please wait a moment and try logging in again");
    }

    @PostMapping("/auth/register")
    public Mono<ResponseEntity<Map<String, Object>>> authRegisterFallback() {
        log.error("🔴 Auth service register fallback triggered");
        return buildFallbackResponse(
                "auth",
                "Registration service is currently unavailable",
                "Your account is safe. Please try registering again in a few moments");
    }

    /**
     * User Service Fallback
     */
    @GetMapping("/user/profile")
    public Mono<ResponseEntity<Map<String, Object>>> userProfileFallback() {
        log.error("🔴 User service profile fallback triggered");
        return buildFallbackResponse(
                "user",
                "Unable to load user profile",
                "Your profile data is safe. Please refresh the page");
    }

    @PutMapping("/user/profile")
    public Mono<ResponseEntity<Map<String, Object>>> userProfileUpdateFallback() {
        log.error("🔴 User service update fallback triggered");
        return buildFallbackResponse(
                "user",
                "Unable to update profile at this time",
                "Your changes have not been saved. Please try again later",
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    /**
     * Job Service Fallback
     */
    @GetMapping("/job/**")
    public Mono<ResponseEntity<Map<String, Object>>> jobServiceFallback() {
        log.error("🔴 Job service fallback triggered");
        return buildFallbackResponse(
                "job",
                "Job listings are temporarily unavailable",
                "Please check back in a few moments for the latest opportunities");
    }

    /**
     * CV Service Fallback
     */
    @PostMapping("/cv/upload")
    public Mono<ResponseEntity<Map<String, Object>>> cvUploadFallback() {
        log.error("🔴 CV service upload fallback triggered");
        return buildFallbackResponse(
                "cv",
                "CV upload service is temporarily unavailable",
                "Your file has not been uploaded. Please save it and try again later",
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    /**
     * Application Service Fallback
     */
    @PostMapping("/application/**")
    public Mono<ResponseEntity<Map<String, Object>>> applicationFallback() {
        log.error("🔴 Application service fallback triggered");
        return buildFallbackResponse(
                "application",
                "Job application service is temporarily unavailable",
                "Your application has not been submitted. Please try again later",
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    /**
     * Notification Service Fallback
     */
    @GetMapping("/notification/**")
    public Mono<ResponseEntity<Map<String, Object>>> notificationFallback() {
        log.warn("🔴 Notification service fallback triggered");
        return buildFallbackResponse(
                "notification",
                "Notifications are temporarily unavailable",
                "You may have missed notifications. Please refresh to check for updates",
                HttpStatus.OK // Not critical, don't alarm user
        );
    }

    /**
     * Health Check Fallback (should rarely trigger)
     */
    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, Object>>> healthFallback() {
        log.error("🔴 Health check fallback triggered - critical issue!");
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status", "DOWN",
                        "message", "Gateway is experiencing issues",
                        "timestamp", LocalDateTime.now().toString(),
                        "suggestion", "Please contact system administrator if this persists")));
    }

    // ========== Helper Methods ==========

    private Mono<ResponseEntity<Map<String, Object>>> buildFallbackResponse(
            String service,
            String message,
            String suggestion) {
        return buildFallbackResponse(service, message, suggestion, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @SuppressWarnings("null")
    private Mono<ResponseEntity<Map<String, Object>>> buildFallbackResponse(
            String service,
            String message,
            String suggestion,
            HttpStatus status) {

        return Mono.just(ResponseEntity
                .status(status)
                .body(Map.of(
                        "status", status.value(),
                        "error", status.getReasonPhrase(),
                        "message", message,
                        "service", service,
                        "timestamp", LocalDateTime.now().toString(),
                        "suggestion", suggestion,
                        "fallback", true)));
    }
}
