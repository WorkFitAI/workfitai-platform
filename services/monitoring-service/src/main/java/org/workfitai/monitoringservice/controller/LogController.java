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
import java.util.ArrayList;
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

    @PostMapping("/search")
    public ResponseEntity<LogSearchResponse> searchLogs(@RequestBody LogSearchRequest request) {
        log.debug("Searching logs with request: {}", request);
        return ResponseEntity.ok(logSearchService.searchLogs(request));
    }

    /**
     * Search logs via query parameters.
     * Supports both Instant format (2024-01-01T00:00:00Z) and LocalDateTime (2024-01-01T00:00:00).
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

        List<String> allLevels = mergeLevels(level, levels);
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

    @GetMapping({ "/statistics", "/stats" })
    public ResponseEntity<LogStatistics> getStatistics(@RequestParam(defaultValue = "24") int hours) {
        log.debug("Getting log statistics for last {} hours", hours);
        return ResponseEntity.ok(logSearchService.getStatistics(hours));
    }

    @GetMapping("/trace/{traceId}")
    public ResponseEntity<List<LogEntry>> getLogsByTraceId(@PathVariable String traceId) {
        log.debug("Getting logs for traceId: {}", traceId);
        return ResponseEntity.ok(logSearchService.getLogsByTraceId(traceId));
    }

    @GetMapping("/errors/recent")
    public ResponseEntity<List<LogEntry>> getRecentErrors(
            @RequestParam(defaultValue = "15") int minutes) {
        log.debug("Getting recent errors for last {} minutes", minutes);
        return ResponseEntity.ok(logSearchService.getRecentErrors(minutes));
    }

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

    @GetMapping("/activity/user/{username}")
    public ResponseEntity<UserActivityResponse> getUserActivityByUsername(
            @PathVariable String username,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {

        log.debug("Getting activity for user: {} for last {} hours", username, hours);

        Instant fromInstant = parseDateTime(from) != null ? parseDateTime(from)
                : Instant.now().minusSeconds(hours * 3600L);
        Instant toInstant = parseDateTime(to) != null ? parseDateTime(to) : Instant.now();

        LogSearchRequest request = LogSearchRequest.builder()
                .username(username)
                .from(fromInstant)
                .to(toInstant)
                .page(page)
                .size(size)
                .build();

        return ResponseEntity.ok(logSearchService.getUserActivity(request, true));
    }

    @GetMapping("/activity/online")
    public ResponseEntity<UserActivityResponse> getOnlineUsers(
            @RequestParam(defaultValue = "15") int minutes) {

        log.debug("Getting online users for last {} minutes", minutes);

        LogSearchRequest request = LogSearchRequest.builder()
                .from(Instant.now().minusSeconds(minutes * 60L))
                .to(Instant.now())
                .size(500)
                .build();

        return ResponseEntity.ok(logSearchService.getUserActivity(request, true));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    /** Merges a single level string into the levels list, deduplicating and uppercasing. */
    private List<String> mergeLevels(String level, List<String> levels) {
        List<String> merged = levels != null ? new ArrayList<>(levels) : new ArrayList<>();
        if (level != null && !level.isBlank() && !merged.contains(level.toUpperCase())) {
            merged.add(level.toUpperCase());
        }
        return merged;
    }

    /**
     * Parses a datetime string to Instant.
     * Supports ISO instant (with Z), LocalDateTime (without Z), and date-only formats.
     */
    private Instant parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isBlank()) {
            return null;
        }
        try {
            if (dateTimeStr.endsWith("Z")) {
                return Instant.parse(dateTimeStr);
            }
            if (dateTimeStr.contains("T")) {
                return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        .toInstant(ZoneOffset.UTC);
            }
            return LocalDateTime.parse(dateTimeStr + "T00:00:00", DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            log.warn("Failed to parse datetime string: {}", dateTimeStr, e);
            return null;
        }
    }
}
