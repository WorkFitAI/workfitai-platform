package org.workfitai.monitoringservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * DTO representing a log entry from Elasticsearch.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogEntry {

    private String id;
    private Instant timestamp;
    private String level;
    private String service;
    private String logger;
    private String message;
    private String thread;
    private String traceId;
    private String spanId;
    private String userId;
    private String username;
    private String exception;
    private String stackTrace;
    private Map<String, Object> extra;
}
