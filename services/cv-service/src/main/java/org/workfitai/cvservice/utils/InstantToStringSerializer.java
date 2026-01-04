package org.workfitai.cvservice.utils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class InstantToStringSerializer extends JsonSerializer<Instant> {
    @Override
    public void serialize(Instant value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
        } else {
            String formatted = value.atZone(ZoneId.of("GMT+7")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            gen.writeString(formatted);
        }
    }
}
