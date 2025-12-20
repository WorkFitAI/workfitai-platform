-- =============================================================================
-- WorkFitAI Fluent Bit Lua Script
-- Purpose: Extract, normalize and enrich log fields for better Kibana display
-- =============================================================================

-- Service name mapping from container names (use Lua patterns, not regex)
local SERVICE_NAMES = {
    "auth-service",
    "user-service",
    "job-service",
    "cv-service",
    "notification-service",
    "application-service",
    "api-gateway",
    "monitoring-service"
}

-- Helper function to check if string contains service name
local function find_service_name(text)
    if not text then return nil end
    text = tostring(text)
    for _, service in ipairs(SERVICE_NAMES) do
        if string.find(text, service, 1, true) then
            return service
        end
    end
    return nil
end

-- =============================================================================
-- Function: extract_container_info
-- Purpose: Extract container name and service name from Docker container
-- Called FIRST in the pipeline to identify the service
-- =============================================================================
function extract_container_info(tag, timestamp, record)
    local new_record = {}
    
    -- Copy all existing fields
    for k, v in pairs(record) do
        new_record[k] = v
    end
    
    -- Try to extract container name from the tag
    -- Tag format: docker.var.lib.docker.containers.<container_id>.<container_id>-json.log
    local container_id = nil
    if tag then
        container_id = string.match(tag, "docker%.var%.lib%.docker%.containers%.([a-f0-9]+)%.")
    end
    
    -- Store container ID for reference
    if container_id then
        new_record["container_id"] = string.sub(container_id, 1, 12)
    end
    
    -- Try to extract service name from multiple sources
    local service_name = nil
    
    -- Method 1: From Docker attrs.tag (set by docker-compose logging config)
    -- Format: "container_name/service-name"
    if record["attrs"] and type(record["attrs"]) == "table" then
        local attrs = record["attrs"]
        if attrs["tag"] then
            service_name = find_service_name(attrs["tag"])
        end
    end
    
    -- Method 2: From log content (Spring Boot logs contain service info)
    if not service_name and record["log"] then
        service_name = find_service_name(record["log"])
    end
    
    -- Method 3: From container_name field if present
    if not service_name and record["container_name"] then
        service_name = find_service_name(record["container_name"])
    end
    
    -- Method 4: From service field (Spring Boot JSON log)
    if not service_name and record["service"] then
        service_name = find_service_name(record["service"])
    end
    
    new_record["service_name"] = service_name or "unknown"
    
    return 1, timestamp, new_record
end

-- =============================================================================
-- Function: normalize_log_fields
-- Purpose: Normalize and enrich log fields for consistent Kibana display
-- Called AFTER JSON parsing to process Spring Boot log fields
-- =============================================================================
function normalize_log_fields(tag, timestamp, record)
    local new_record = {}
    
    -- Copy all existing fields
    for k, v in pairs(record) do
        new_record[k] = v
    end
    
    -- ==========================================================================
    -- SERVICE NAME: Get from Spring Boot 'service' field if available
    -- ==========================================================================
    if record["service"] and record["service"] ~= "" then
        -- Convert Spring Boot service name to full service name
        local svc = record["service"]
        if not string.find(svc, "-service", 1, true) and svc ~= "api-gateway" then
            svc = svc .. "-service"
        end
        new_record["service_name"] = svc
    end
    
    -- ==========================================================================
    -- LOG LEVEL: Normalize log level to uppercase
    -- ==========================================================================
    local level = record["level"] or record["log_level"]
    if level then
        new_record["log_level"] = string.upper(tostring(level))
    else
        new_record["log_level"] = "INFO"
    end
    
    -- ==========================================================================
    -- LOG MESSAGE: Extract clean message
    -- ==========================================================================
    if record["message"] and record["message"] ~= "" then
        new_record["log_message"] = record["message"]
    elseif record["log"] and type(record["log"]) == "string" then
        new_record["log_message"] = record["log"]
    end
    
    -- ==========================================================================
    -- LOGGER: Normalize logger name with short class name
    -- ==========================================================================
    if record["logger_name"] then
        new_record["logger"] = record["logger_name"]
        -- Extract short class name for cleaner display
        local short_logger = string.match(record["logger_name"], "([^%.]+)$")
        if short_logger then
            new_record["logger_class"] = short_logger
        end
    end
    
    -- ==========================================================================
    -- THREAD: Normalize thread name
    -- ==========================================================================
    if record["thread_name"] then
        new_record["thread"] = record["thread_name"]
    end
    
    -- ==========================================================================
    -- USER INFO: Extract user information from MDC
    -- ==========================================================================
    if record["username"] and record["username"] ~= "" and record["username"] ~= "anonymous" then
        new_record["user"] = record["username"]
    end
    if record["userId"] and record["userId"] ~= "" then
        new_record["user_id"] = record["userId"]
    end
    if record["roles"] and record["roles"] ~= "" then
        new_record["user_roles"] = record["roles"]
    end
    
    -- ==========================================================================
    -- LOG CLASSIFICATION: Extract log type and action context
    -- ==========================================================================
    if record["log_type"] and record["log_type"] ~= "" then
        new_record["log_type"] = record["log_type"]
    else
        -- Default to SYSTEM if not specified
        new_record["log_type"] = "SYSTEM"
    end
    
    if record["action"] and record["action"] ~= "" then
        new_record["action"] = record["action"]
    end
    
    if record["entity_type"] and record["entity_type"] ~= "" then
        new_record["entity_type"] = record["entity_type"]
    end
    
    if record["entity_id"] and record["entity_id"] ~= "" then
        new_record["entity_id"] = record["entity_id"]
    end
    
    -- ==========================================================================
    -- REQUEST TRACING: Extract request/trace information
    -- ==========================================================================
    if record["requestId"] and record["requestId"] ~= "" then
        new_record["request_id"] = record["requestId"]
    end
    if record["traceId"] and record["traceId"] ~= "" then
        new_record["trace_id"] = record["traceId"]
    end
    if record["spanId"] and record["spanId"] ~= "" then
        new_record["span_id"] = record["spanId"]
    end
    
    -- ==========================================================================
    -- HTTP INFO: Extract HTTP request details
    -- ==========================================================================
    if record["path"] and record["path"] ~= "" then
        new_record["http_path"] = record["path"]
    end
    if record["method"] and record["method"] ~= "" then
        new_record["http_method"] = string.upper(record["method"])
    end
    
    -- ==========================================================================
    -- EXCEPTION: Extract exception/stack trace info
    -- ==========================================================================
    if record["stack_trace"] then
        new_record["exception"] = record["stack_trace"]
        new_record["has_exception"] = true
    end
    
    -- ==========================================================================
    -- TIMESTAMP: Ensure proper timestamp
    -- ==========================================================================
    if not new_record["@timestamp"] then
        if record["time"] then
            new_record["@timestamp"] = record["time"]
        end
    end
    
    -- ==========================================================================
    -- CLEAN UP: Remove verbose fields that are already extracted
    -- ==========================================================================
    new_record["level"] = nil
    new_record["thread_name"] = nil
    new_record["logger_name"] = nil
    new_record["mdc"] = nil
    new_record["context"] = nil
    
    return 1, timestamp, new_record
end

-- =============================================================================
-- Keep old function for backwards compatibility
-- =============================================================================
function extract_service_name(tag, timestamp, record)
    return normalize_log_fields(tag, timestamp, record)
end
