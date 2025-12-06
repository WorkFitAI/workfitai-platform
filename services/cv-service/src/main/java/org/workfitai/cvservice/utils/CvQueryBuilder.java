package org.workfitai.cvservice.utils;


import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.Collection;
import java.util.Map;


/**
 * Utility class to build dynamic MongoDB queries for CV filtering.
 */
public final class CvQueryBuilder {


    private CvQueryBuilder() {
        // private constructor to prevent instantiation
    }


    /**
     * Build Mongo Query for CV list by username and optional filters.
     *
     * @param username the owner of the CVs
     * @param filters  map of filter key-values
     * @return Query object ready for mongoTemplate
     */
    public static Query build(String username, Map<String, Object> filters) {
        Query query = new Query();


        // Always filter by user + isExist
        query.addCriteria(Criteria.where("belongTo").is(username).and("isExist").is(true));


        applyTemplateType(query, filters);
        applySectionFilters(query, filters);


        return query;
    }


    private static void applyTemplateType(Query query, Map<String, Object> filters) {
        Object templateType = filters.remove("templateType");
        if (templateType == null) return;


        if (templateType instanceof String str) {
            query.addCriteria(Criteria.where("templateType").is(str));
        } else if (templateType instanceof Enum<?> e) {
            query.addCriteria(Criteria.where("templateType").is(e.name()));
        }
    }


    private static void applySectionFilters(Query query, Map<String, Object> filters) {
        filters.forEach((key, value) -> {
            String field = "sections." + key;


            if (value instanceof Collection<?> coll && !coll.isEmpty()) {
                query.addCriteria(Criteria.where(field).in(coll));
            } else if (value instanceof Enum<?> e) {
                query.addCriteria(Criteria.where(field).is(e.name()));
            } else {
                query.addCriteria(Criteria.where(field).is(value));
            }
        });
    }
}

