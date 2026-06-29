package org.workfitai.userservice.converter;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StringListConverterTest {

    private final StringListConverter converter = new StringListConverter();

    // ---- convertToDatabaseColumn ----

    @Test
    void toColumn_nullList_returnsNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void toColumn_emptyList_returnsNull() {
        assertThat(converter.convertToDatabaseColumn(new ArrayList<>())).isNull();
    }

    @Test
    void toColumn_nonEmptyList_returnsJson() {
        String result = converter.convertToDatabaseColumn(List.of("Java", "Spring"));
        assertThat(result).contains("Java").contains("Spring");
    }

    @Test
    void toColumn_singleElement_returnsJsonArray() {
        String result = converter.convertToDatabaseColumn(List.of("Go"));
        assertThat(result).isEqualTo("[\"Go\"]");
    }

    // ---- convertToEntityAttribute ----

    @Test
    void toEntity_nullString_returnsEmptyList() {
        assertThat(converter.convertToEntityAttribute(null)).isEmpty();
    }

    @Test
    void toEntity_blankString_returnsEmptyList() {
        assertThat(converter.convertToEntityAttribute("  ")).isEmpty();
    }

    @Test
    void toEntity_validJson_returnsList() {
        List<String> result = converter.convertToEntityAttribute("[\"Java\",\"Spring\"]");
        assertThat(result).containsExactly("Java", "Spring");
    }

    @Test
    void toEntity_invalidJson_throwsIllegalArgument() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("not-valid-json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Error converting JSON to skills list");
    }

    @Test
    void roundTrip_preservesList() {
        List<String> original = List.of("Kotlin", "Docker", "Kafka");
        String json = converter.convertToDatabaseColumn(original);
        List<String> back = converter.convertToEntityAttribute(json);
        assertThat(back).containsExactlyElementsOf(original);
    }
}
