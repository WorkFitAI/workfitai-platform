package org.workfitai.cvservice.utils;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Query;
import org.workfitai.cvservice.model.enums.TemplateType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CvQueryBuilderTest {

    @Test
    void build_alwaysFiltersByBelongToAndIsExist() {
        Query query = CvQueryBuilder.build("alice", new HashMap<>());

        Document criteria = query.getQueryObject();
        assertThat(criteria.get("belongTo")).isEqualTo("alice");
        assertThat(criteria.get("isExist")).isEqualTo(true);
    }

    @Test
    void build_appliesTemplateTypeAsString_andRemovesKeyFromSectionFilters() {
        Map<String, Object> filters = new HashMap<>();
        filters.put("templateType", "TECH");

        Query query = CvQueryBuilder.build("alice", filters);

        Document criteria = query.getQueryObject();
        assertThat(criteria.get("templateType")).isEqualTo("TECH");
        assertThat(criteria.containsKey("sections.templateType")).isFalse();
    }

    @Test
    void build_appliesTemplateTypeAsEnumName() {
        Map<String, Object> filters = new HashMap<>();
        filters.put("templateType", TemplateType.TECH);

        Query query = CvQueryBuilder.build("alice", filters);

        assertThat(query.getQueryObject().get("templateType")).isEqualTo("TECH");
    }

    @Test
    void build_ignoresMissingTemplateType() {
        Query query = CvQueryBuilder.build("alice", new HashMap<>());

        assertThat(query.getQueryObject().containsKey("templateType")).isFalse();
    }

    @Test
    void build_appliesSectionFilter_forNonEmptyCollectionValue_usesInOperator() {
        Map<String, Object> filters = new HashMap<>();
        filters.put("skills", List.of("Java", "Spring"));

        Query query = CvQueryBuilder.build("alice", filters);

        Document sectionsSkills = (Document) query.getQueryObject().get("sections.skills");
        assertThat(sectionsSkills).containsKey("$in");
    }

    @Test
    void build_appliesSectionFilter_forEnumValue_usesEnumName() {
        Map<String, Object> filters = new HashMap<>();
        filters.put("templateTypePreference", TemplateType.UPLOAD);

        Query query = CvQueryBuilder.build("alice", filters);

        assertThat(query.getQueryObject().get("sections.templateTypePreference")).isEqualTo("UPLOAD");
    }

    @Test
    void build_appliesSectionFilter_forPlainValue_usesEqualityOperator() {
        Map<String, Object> filters = new HashMap<>();
        filters.put("yearsOfExperience", 5);

        Query query = CvQueryBuilder.build("alice", filters);

        assertThat(query.getQueryObject().get("sections.yearsOfExperience")).isEqualTo(5);
    }

    /**
     * KNOWN BUG (pre-existing, out of scope for this test phase): applyTemplateType calls
     * filters.remove(...) unconditionally, which throws on any immutable Map (e.g. Map.of()).
     * Pinned here as documented current behavior rather than silently worked around.
     */
    @Test
    void build_throwsUnsupportedOperationException_whenFiltersMapIsImmutable() {
        assertThatThrownBy(() -> CvQueryBuilder.build("alice", Map.of()))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
