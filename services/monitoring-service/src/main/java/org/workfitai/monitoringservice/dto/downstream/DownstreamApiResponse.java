package org.workfitai.monitoringservice.dto.downstream;

/**
 * Generic wrapper for downstream service responses.
 * All services (application-service, job-service, etc.) wrap their payloads in
 * {@code RestResponse<T>} with {status, message, data} shape.
 * Feign must deserialize into this type first, then callers extract {@link #data()}.
 */
public record DownstreamApiResponse<T>(int status, String message, T data) {}
