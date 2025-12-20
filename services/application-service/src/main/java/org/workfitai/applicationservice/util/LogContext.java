package org.workfitai.applicationservice.util;

import org.slf4j.MDC;
import org.workfitai.applicationservice.constants.LogType;

/**
 * Utility for managing logging context (MDC) with log type classification.
 * Thread-safe using SLF4J's MDC (ThreadLocal storage).
 * 
 * Usage pattern:
 * <pre>
 * public void createJob(JobRequest request) {
 *     LogContext.setLogType(LogType.USER_ACTION);
 *     LogContext.setAction("CREATE_JOB");
 *     log.info("Creating job: {}", request.getTitle());
 *     // ... business logic
 *     LogContext.clear(); // Optional, filter will clear at request end
 * }
 * </pre>
 */
public class LogContext {
    
    private static final String LOG_TYPE_KEY = "log_type";
    private static final String ACTION_KEY = "action";
    private static final String ENTITY_TYPE_KEY = "entity_type";
    private static final String ENTITY_ID_KEY = "entity_id";
    
    /**
     * Set the log type for current thread context.
     * @param logType Type of log event
     */
    public static void setLogType(LogType logType) {
        if (logType != null) {
            MDC.put(LOG_TYPE_KEY, logType.name());
        }
    }
    
    /**
     * Set a human-readable action description.
     * Examples: "CREATE_JOB", "UPDATE_PROFILE", "APPLY_JOB"
     */
    public static void setAction(String action) {
        if (action != null && !action.isBlank()) {
            MDC.put(ACTION_KEY, action);
        }
    }
    
    /**
     * Set the entity type being operated on.
     * Examples: "Job", "User", "Application", "CV"
     */
    public static void setEntityType(String entityType) {
        if (entityType != null && !entityType.isBlank()) {
            MDC.put(ENTITY_TYPE_KEY, entityType);
        }
    }
    
    /**
     * Set the entity ID being operated on.
     */
    public static void setEntityId(String entityId) {
        if (entityId != null && !entityId.isBlank()) {
            MDC.put(ENTITY_ID_KEY, entityId);
        }
    }
    
    /**
     * Convenience method to set multiple context values.
     */
    public static void setContext(LogType logType, String action, String entityType, String entityId) {
        setLogType(logType);
        setAction(action);
        setEntityType(entityType);
        setEntityId(entityId);
    }
    
    /**
     * Clear log context (usually called at end of request).
     */
    public static void clear() {
        MDC.remove(LOG_TYPE_KEY);
        MDC.remove(ACTION_KEY);
        MDC.remove(ENTITY_TYPE_KEY);
        MDC.remove(ENTITY_ID_KEY);
    }
    
    /**
     * Get current log type.
     */
    public static String getLogType() {
        return MDC.get(LOG_TYPE_KEY);
    }
}
