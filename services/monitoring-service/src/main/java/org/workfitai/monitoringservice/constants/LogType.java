package org.workfitai.monitoringservice.constants;

/**
 * Enum defining types of logs for better filtering and analysis in monitoring dashboard.
 * 
 * Usage in code:
 * <pre>
 * // For user actions
 * LogContext.setLogType(LogType.USER_ACTION);
 * log.info("User {} created job posting", username);
 * 
 * // For system events
 * LogContext.setLogType(LogType.SYSTEM);
 * log.info("Kafka consumer connected");
 * </pre>
 */
public enum LogType {
    
    /**
     * User-initiated actions (API calls with authenticated user).
     * Examples: Create job, update profile, upload CV, apply for job
     */
    USER_ACTION,
    
    /**
     * System operations (scheduled tasks, background jobs, startup).
     * Examples: Database migration, Kafka consumer, cache warming
     */
    SYSTEM,
    
    /**
     * Health checks, actuator endpoints, monitoring pings.
     * Examples: /actuator/health, /metrics, Consul checks
     */
    HEALTH_CHECK,
    
    /**
     * Authentication/authorization events.
     * Examples: Login attempts, token validation, 2FA verification
     */
    AUTH,
    
    /**
     * Inter-service communication (Kafka events, REST calls between services).
     * Examples: Kafka message processing, Feign client calls
     */
    SERVICE_TO_SERVICE
}
