package org.workfitai.monitoringservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.workfitai.monitoringservice.dto.LogEntry;
import org.workfitai.monitoringservice.dto.LogSearchRequest;
import org.workfitai.monitoringservice.dto.LogSearchResponse;
import org.workfitai.monitoringservice.dto.LogStatistics;
import org.workfitai.monitoringservice.service.LogSearchService;

import java.time.Instant;
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
     */
    @GetMapping("/search")
    public ResponseEntity<LogSearchResponse> searchLogsGet(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) List<String> levels,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String traceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        LogSearchRequest request = LogSearchRequest.builder()
                .query(query)
                .service(service)
                .levels(levels)
                .from(from)
                .to(to)
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
    @GetMapping("/statistics")
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
}
