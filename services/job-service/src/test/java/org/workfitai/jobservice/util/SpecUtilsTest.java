package org.workfitai.jobservice.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpecUtilsTest {

    @SuppressWarnings("unchecked")
    private final Specification<Object> base = mock(Specification.class);
    @SuppressWarnings("unchecked")
    private final Specification<Object> extra = mock(Specification.class);

    @Test
    void and_returnsExtra_whenBaseIsNull() {
        assertThat(SpecUtils.and(null, extra)).isSameAs(extra);
    }

    @Test
    void and_combinesBaseAndExtra_whenBaseIsNotNull() {
        Specification<Object> expected = mock(Specification.class);
        when(base.and(extra)).thenReturn(expected);

        Specification<Object> combined = SpecUtils.and(base, extra);

        assertThat(combined).isSameAs(expected);
    }

    @Test
    void or_returnsExtra_whenBaseIsNull() {
        assertThat(SpecUtils.or(null, extra)).isSameAs(extra);
    }

    @Test
    void or_combinesBaseAndExtra_whenBaseIsNotNull() {
        Specification<Object> expected = mock(Specification.class);
        when(base.or(extra)).thenReturn(expected);

        Specification<Object> combined = SpecUtils.or(base, extra);

        assertThat(combined).isSameAs(expected);
    }
}
