package org.workfitai.monitoringservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * DTO representing a log entry from Elasticsearch.
 * Uses @JsonInclude(NON_NULL) to exclude null fields from response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LogEntry {

    private String id;
    private Instant timestamp;
    private String level;
    private String service;
    private String logger;
    private String message;
    private String thread;

    // HTTP Request info (for API activity logs)
    private String method;
    private String path;
    private String requestId;

    // User info
    private String userId;
    private String username;
    private String roles;

    // Distributed tracing
    private String traceId;
    private String spanId;

    // Exception info (only present when there's an error)
    private String exception;
    private String stackTrace;

    // Any extra fields from ES
    private Map<String, Object> extra;
}
