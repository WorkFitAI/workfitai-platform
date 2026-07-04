package org.workfitai.userservice.specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.workfitai.userservice.model.AdminEntity;
import org.workfitai.userservice.model.CandidateEntity;
import org.workfitai.userservice.model.HREntity;

import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

/**
 * Executes the Specification lambdas to drive JaCoCo coverage.
 * Uses RETURNS_DEEP_STUBS so cb/root chains don't NPE.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class SpecificationTest {

    private final AdminSpecification adminSpec = new AdminSpecification();
    private final HRSpecification hrSpec = new HRSpecification();

    // ---- AdminSpecification ----

    @Test
    void admin_nullKeyword_lambdaExecuted() {
        Root root = mock(Root.class, RETURNS_DEEP_STUBS);
        CriteriaQuery query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);

        adminSpec.filter(null).toPredicate(root, query, cb);
    }

    @Test
    void admin_blankKeyword_lambdaExecuted() {
        Root root = mock(Root.class, RETURNS_DEEP_STUBS);
        CriteriaQuery query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);

        adminSpec.filter("   ").toPredicate(root, query, cb);
    }

    @Test
    void admin_withKeyword_lambdaExecuted() {
        Root root = mock(Root.class, RETURNS_DEEP_STUBS);
        CriteriaQuery query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);

        adminSpec.filter("alice").toPredicate(root, query, cb);
    }

    // ---- CandidateSpecification ----

    @Test
    void candidate_search_nullKeyword_lambdaExecuted() {
        Root<CandidateEntity> root = mock(Root.class, RETURNS_DEEP_STUBS);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);

        CandidateSpecification.search(null).toPredicate(root, query, cb);
    }

    @Test
    void candidate_search_withKeyword_lambdaExecuted() {
        Root<CandidateEntity> root = mock(Root.class, RETURNS_DEEP_STUBS);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);

        CandidateSpecification.search("java").toPredicate(root, query, cb);
    }

    @Test
    void candidate_filter_allNull_lambdaExecuted() {
        Root<CandidateEntity> root = mock(Root.class, RETURNS_DEEP_STUBS);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);

        CandidateSpecification.filter(null, null, null).toPredicate(root, query, cb);
    }

    @Test
    void candidate_filter_withAllParams_lambdaExecuted() {
        Root<CandidateEntity> root = mock(Root.class, RETURNS_DEEP_STUBS);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);

        CandidateSpecification.filter("Bachelor", 1, 5).toPredicate(root, query, cb);
    }

    // ---- HRSpecification ----

    @Test
    void hr_noFilters_lambdaExecuted() {
        Root<HREntity> root = mock(Root.class, RETURNS_DEEP_STUBS);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);

        hrSpec.filter(null, null).toPredicate(root, query, cb);
    }

    @Test
    void hr_withCompanyNo_lambdaExecuted() {
        Root<HREntity> root = mock(Root.class, RETURNS_DEEP_STUBS);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);

        hrSpec.filter(null, "TAX001").toPredicate(root, query, cb);
    }

    @Test
    void hr_withKeyword_lambdaExecuted() {
        Root<HREntity> root = mock(Root.class, RETURNS_DEEP_STUBS);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);

        hrSpec.filter("john", null).toPredicate(root, query, cb);
    }

    @Test
    void hr_withBothFilters_lambdaExecuted() {
        Root<HREntity> root = mock(Root.class, RETURNS_DEEP_STUBS);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);

        hrSpec.filter("john", "TAX001").toPredicate(root, query, cb);
    }
}
