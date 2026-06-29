package org.workfitai.cvservice.utils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class InstantToStringSerializerTest {

    private final InstantToStringSerializer serializer = new InstantToStringSerializer();

    @Test
    void serialize_writesFormattedString_whenValuePresent() throws Exception {
        JsonGenerator gen = mock(JsonGenerator.class);
        SerializerProvider provider = mock(SerializerProvider.class);
        Instant value = Instant.parse("2026-01-15T03:00:00Z");

        serializer.serialize(value, gen, provider);

        verify(gen).writeString("2026-01-15 10:00:00");
        verify(gen, never()).writeNull();
    }

    @Test
    void serialize_writesNull_whenValueAbsent() throws Exception {
        JsonGenerator gen = mock(JsonGenerator.class);
        SerializerProvider provider = mock(SerializerProvider.class);

        serializer.serialize(null, gen, provider);

        verify(gen).writeNull();
    }
}
