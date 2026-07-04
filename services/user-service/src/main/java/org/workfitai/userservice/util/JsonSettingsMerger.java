package org.workfitai.userservice.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Recursive merge-patch for JSONB settings columns. Shared across settings
 * services so candidate/HR partial updates behave consistently.
 */
public final class JsonSettingsMerger {

    private JsonSettingsMerger() {
    }

    public static JsonNode merge(JsonNode current, JsonNode update) {
        if (current == null || current.isNull()) {
            return update;
        }

        ObjectNode merged = current.deepCopy();

        update.fields().forEachRemaining(entry -> {
            String fieldName = entry.getKey();
            JsonNode value = entry.getValue();

            if (value.isObject() && merged.has(fieldName) && merged.get(fieldName).isObject()) {
                JsonNode mergedNested = merge(merged.get(fieldName), value);
                merged.set(fieldName, mergedNested);
            } else if (!value.isNull()) {
                merged.set(fieldName, value);
            }
        });

        return merged;
    }
}
