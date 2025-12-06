-- Lua script to normalize log fields for better Kibana display
function normalize_log_fields(tag, timestamp, record)
    local new_record = {}
    
    -- Copy all existing fields
    for k, v in pairs(record) do
        new_record[k] = v
    end
    
    -- Extract service name from the 'service' field (from logstash-logback-encoder)
    -- or from container name
    if record["service"] then
        new_record["service_name"] = record["service"]
    elseif record["container_name"] then
        local name = string.gsub(record["container_name"], "^/", "")
        -- Extract service name from container name (e.g., "workfitai-auth-service-1" -> "auth-service")
        local service = string.match(name, "([%w%-]+%-service)")
        if service then
            new_record["service_name"] = service
        else
            new_record["service_name"] = name
        end
    else
        new_record["service_name"] = "unknown"
    end
    
    -- Normalize log level field
    local level = record["level"] or record["log.level"] or record["severity"]
    if level then
        new_record["log_level"] = string.upper(tostring(level))
    elseif record["log"] then
        -- Try to extract from log message
        local log = tostring(record["log"])
        if string.find(log, "ERROR") then
            new_record["log_level"] = "ERROR"
        elseif string.find(log, "WARN") then
            new_record["log_level"] = "WARN"
        elseif string.find(log, "INFO") then
            new_record["log_level"] = "INFO"
        elseif string.find(log, "DEBUG") then
            new_record["log_level"] = "DEBUG"
        elseif string.find(log, "TRACE") then
            new_record["log_level"] = "TRACE"
        end
    end
    
    -- Extract message field
    if record["message"] then
        new_record["log_message"] = record["message"]
    elseif record["msg"] then
        new_record["log_message"] = record["msg"]
    elseif record["log"] and type(record["log"]) == "string" then
        new_record["log_message"] = record["log"]
    end
    
    -- Normalize logger name
    if record["logger_name"] then
        new_record["logger"] = record["logger_name"]
    elseif record["logger"] then
        new_record["logger"] = record["logger"]
    end
    
    -- Normalize thread name
    if record["thread_name"] then
        new_record["thread"] = record["thread_name"]
    end
    
    -- Extract username if present in MDC
    if record["username"] then
        new_record["user"] = record["username"]
    end
    
    -- Extract requestId if present
    if record["requestId"] then
        new_record["request_id"] = record["requestId"]
    end
    
    -- Extract path/method for HTTP request logging
    if record["path"] then
        new_record["http_path"] = record["path"]
    end
    if record["method"] then
        new_record["http_method"] = record["method"]
    end
    
    -- Add timestamp if not present
    if not new_record["@timestamp"] and record["time"] then
        new_record["@timestamp"] = record["time"]
    end
    
    return 1, timestamp, new_record
end

-- Keep the old function for backwards compatibility
function extract_service_name(tag, timestamp, record)
    return normalize_log_fields(tag, timestamp, record)
end
