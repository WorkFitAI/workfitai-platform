-- Lua script to extract service name from Docker container name
function extract_service_name(tag, timestamp, record)
    -- Extract container ID from tag (docker.var.lib.docker.containers.<container_id>.<container_id>-json.log)
    local container_name = record["container_name"]
    
    if container_name then
        -- Remove leading slash if present
        container_name = string.gsub(container_name, "^/", "")
        record["service_name"] = container_name
    else
        -- Try to extract from source field or use unknown
        record["service_name"] = "unknown"
    end
    
    -- Extract log level from message if not already present
    if record["level"] == nil and record["log"] then
        local log = record["log"]
        if string.find(log, "ERROR") then
            record["level"] = "ERROR"
        elseif string.find(log, "WARN") then
            record["level"] = "WARN"
        elseif string.find(log, "INFO") then
            record["level"] = "INFO"
        elseif string.find(log, "DEBUG") then
            record["level"] = "DEBUG"
        elseif string.find(log, "TRACE") then
            record["level"] = "TRACE"
        end
    end
    
    return 1, timestamp, record
end
