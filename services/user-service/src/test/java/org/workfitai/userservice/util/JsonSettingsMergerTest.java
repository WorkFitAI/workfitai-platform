package org.workfitai.userservice.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonSettingsMergerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void merge_nullCurrent_returnsUpdate() throws Exception {
        JsonNode update = objectMapper.readTree("{\"email\":true}");

        JsonNode merged = JsonSettingsMerger.merge(null, update);

        assertThat(merged).isSameAs(update);
    }

    @Test
    void merge_recursiveObject_preservesMissingFieldsAndIgnoresNullUpdates() throws Exception {
        JsonNode current = objectMapper.readTree("""
            {
              "email": true,
              "channels": {
                "marketing": true,
                "security": true
              }
            }
            """);
        JsonNode update = objectMapper.readTree("""
            {
              "email": null,
              "channels": {
                "marketing": false
              },
              "push": true
            }
            """);

        JsonNode merged = JsonSettingsMerger.merge(current, update);

        assertThat(merged.get("email").asBoolean()).isTrue();
        assertThat(merged.at("/channels/marketing").asBoolean()).isFalse();
        assertThat(merged.at("/channels/security").asBoolean()).isTrue();
        assertThat(merged.get("push").asBoolean()).isTrue();
        assertThat(current.at("/channels/marketing").asBoolean()).isTrue();
    }

    @Test
    void merge_nonObjectUpdate_replacesCurrentField() throws Exception {
        JsonNode current = objectMapper.readTree("{\"channels\":{\"email\":true}}");
        JsonNode update = objectMapper.readTree("{\"channels\":\"disabled\"}");

        JsonNode merged = JsonSettingsMerger.merge(current, update);

        assertThat(merged.get("channels").asText()).isEqualTo("disabled");
    }
}
