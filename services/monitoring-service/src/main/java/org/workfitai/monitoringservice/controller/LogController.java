package org.workfitai.monitoringservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.workfitai.monitoringservice.dto.*;
import org.workfitai.monitoringservice.service.LogSearchService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * REST controller for log search and analysis endpoints.
 */
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
@Slf4j
public class LogController {

    private final LogSearchService logSearchService;

    /**
     * Search logs with flexible filtering.
     * 
     * @param request Search request with filters
     * @return Paginated log search results
     */
    @PostMapping("/search")
    public ResponseEntity<LogSearchResponse> searchLogs(@RequestBody LogSearchRequest request) {
        log.debug("Searching logs with request: {}", request);
        LogSearchResponse response = logSearchService.searchLogs(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Search logs using query parameters (simpler GET endpoint).
     * Supports both Instant format (2024-01-01T00:00:00Z) and LocalDateTime
     * (2024-01-01T00:00:00).
     */
    @GetMapping("/search")
    public ResponseEntity<LogSearchResponse> searchLogsGet(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) List<String> levels,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String traceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        // Merge single level into levels list
        List<String> allLevels = levels != null ? new java.util.ArrayList<>(levels) : new java.util.ArrayList<>();
        if (level != null && !level.isBlank() && !allLevels.contains(level.toUpperCase())) {
            allLevels.add(level.toUpperCase());
        }

        // Use keyword as query if query is empty
        String searchQuery = (query != null && !query.isBlank()) ? query : keyword;

        LogSearchRequest request = LogSearchRequest.builder()
                .query(searchQuery)
                .service(service)
                .levels(allLevels.isEmpty() ? null : allLevels)
                .username(username)
                .requestId(requestId)
                .from(parseDateTime(from))
                .to(parseDateTime(to))
                .traceId(traceId)
                .page(page)
                .size(size)
                .build();

        return ResponseEntity.ok(logSearchService.searchLogs(request));
    }

    /**
     * Get log statistics for dashboard.
     * 
     * @param hours Time range in hours (default 24)
     * @return Log statistics
     */
    @GetMapping({ "/statistics", "/stats" })
    public ResponseEntity<LogStatistics> getStatistics(
            @RequestParam(defaultValue = "24") int hours) {
        log.debug("Getting log statistics for last {} hours", hours);
        LogStatistics stats = logSearchService.getStatistics(hours);
        return ResponseEntity.ok(stats);
    }

    /**
     * Get logs by trace ID for distributed tracing.
     * 
     * @param traceId The trace ID
     * @return List of logs in the trace
     */
    @GetMapping("/trace/{traceId}")
    public ResponseEntity<List<LogEntry>> getLogsByTraceId(@PathVariable String traceId) {
        log.debug("Getting logs for traceId: {}", traceId);
        List<LogEntry> logs = logSearchService.getLogsByTraceId(traceId);
        return ResponseEntity.ok(logs);
    }

    /**
     * Get recent errors for alerting/monitoring.
     * 
     * @param minutes Time range in minutes (default 15)
     * @return List of recent error logs
     */
    @GetMapping("/errors/recent")
    public ResponseEntity<List<LogEntry>> getRecentErrors(
            @RequestParam(defaultValue = "15") int minutes) {
        log.debug("Getting recent errors for last {} minutes", minutes);
        List<LogEntry> errors = logSearchService.getRecentErrors(minutes);
        return ResponseEntity.ok(errors);
    }

    /**
     * Get logs for a specific service.
     */
    @GetMapping("/service/{serviceName}")
    public ResponseEntity<LogSearchResponse> getLogsByService(
            @PathVariable String serviceName,
            @RequestParam(required = false) List<String> levels,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        LogSearchRequest request = LogSearchRequest.builder()
                .service(serviceName)
                .levels(levels)
                .page(page)
                .size(size)
                .build();

        return ResponseEntity.ok(logSearchService.searchLogs(request));
    }

    /**
     * Get logs for a specific user.
     */
    @GetMapping("/user/{username}")
    public ResponseEntity<LogSearchResponse> getLogsByUser(
            @PathVariable String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        LogSearchRequest request = LogSearchRequest.builder()
                .username(username)
                .page(page)
                .size(size)
                .build();

        return ResponseEntity.ok(logSearchService.searchLogs(request));
    }

    // ==================== USER ACTIVITY ENDPOINTS (For Admin Dashboard)
    // ====================

    /**
     * Get user activity for admin dashboard.
     * Shows what users are doing in the system in a human-readable format.
     * Filters out system logs, health checks, and anonymous requests.
     *
     * @param username       Filter by specific username (optional)
     * @param service        Filter by service (optional)
     * @param from           Start time (optional, defaults to 24h ago)
     * @param to             End time (optional, defaults to now)
     * @param includeSummary Include activity summary statistics
     * @param page           Page number (0-indexed)
     * @param size           Page size
     * @return User activity response with activities and optional summary
     */
    @GetMapping("/activity")
    public ResponseEntity<UserActivityResponse> getUserActivity(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "true") boolean includeSummary,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        log.debug("Getting user activity - username: {}, service: {}, from: {}, to: {}",
                username, service, from, to);

        LogSearchRequest request = LogSearchRequest.builder()
                .username(username)
                .service(service)
                .from(parseDateTime(from))
                .to(parseDateTime(to))
                .page(page)
                .size(size)
                .build();

        return ResponseEntity.ok(logSearchService.getUserActivity(request, includeSummary));
    }

    /**
     * Get activity for a specific user (for admin to view individual user's
     * actions).
     *
     * @param username Username to get activity for
     * @param from     Start time (optional)
     * @param to       End time (optional)
     * @param hours    Time range in hours (default 24, ignored if from/to provided)
     * @param page     Page number
     * @param size     Page size
     * @return User's activity
     */
    @GetMapping("/activity/user/{username}")
    public ResponseEntity<UserActivityResponse> getUserActivityByUsername(
            @PathVariable String username,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {

        log.debug("Getting activity for user: {} for last {} hours", username, hours);

        Instant fromInstant = parseDateTime(from);
        Instant toInstant = parseDateTime(to);

        // Use hours if from/to not provided
        if (fromInstant == null) {
            fromInstant = Instant.now().minusSeconds(hours * 3600L);
        }
        if (toInstant == null) {
            toInstant = Instant.now();
        }

        LogSearchRequest request = LogSearchRequest.builder()
                .username(username)
                .from(fromInstant)
                .to(toInstant)
                .page(page)
                .size(size)
                .build();

        return ResponseEntity.ok(logSearchService.getUserActivity(request, true));
    }

    /**
     * Get online/active users in the last N minutes.
     * Useful for "Currently Active Users" widget.
     *
     * @param minutes Time range in minutes (default 15)
     * @return Activity response with summary of active users
     */
    @GetMapping("/activity/online")
    public ResponseEntity<UserActivityResponse> getOnlineUsers(
            @RequestParam(defaultValue = "15") int minutes) {

        log.debug("Getting online users for last {} minutes", minutes);

        LogSearchRequest request = LogSearchRequest.builder()
                .from(Instant.now().minusSeconds(minutes * 60L))
                .to(Instant.now())
                .size(500) // Get more to have accurate active user count
                .build();

        return ResponseEntity.ok(logSearchService.getUserActivity(request, true));
    }

    // ==================== HELPER METHODS ====================

    /**
     * Parse datetime string to Instant.
     * Supports both ISO Instant format (with Z) and LocalDateTime format (without
     * Z).
     * Examples:
     * - 2024-01-01T00:00:00Z (Instant format)
     * - 2024-01-01T00:00:00 (LocalDateTime, assumed UTC)
     * - 2024-01-01 (Date only, assumed start of day UTC)
     */
    private Instant parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isBlank()) {
            return null;
        }

        try {
            // Try parsing as Instant first (has 'Z' timezone)
            if (dateTimeStr.endsWith("Z")) {
                return Instant.parse(dateTimeStr);
            }

            // Try parsing as LocalDateTime (no timezone - assume UTC)
            if (dateTimeStr.contains("T")) {
                LocalDateTime ldt = LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                return ldt.toInstant(ZoneOffset.UTC);
            }

            // Try parsing as date only (assume start of day UTC)
            LocalDateTime ldt = LocalDateTime.parse(dateTimeStr + "T00:00:00", DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            return ldt.toInstant(ZoneOffset.UTC);

        } catch (DateTimeParseException e) {
            log.warn("Failed to parse datetime string: {}", dateTimeStr, e);
            return null;
        }
    }
}
