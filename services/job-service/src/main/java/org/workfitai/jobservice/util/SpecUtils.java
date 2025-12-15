package org.workfitai.jobservice.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SpecUtils {

    public static <T> Specification<T> and(
            Specification<T> base,
            Specification<T> extra
    ) {
        return base == null ? extra : base.and(extra);
    }

    public static <T> Specification<T> or(
            Specification<T> base,
            Specification<T> extra
    ) {
        return base == null ? extra : base.or(extra);
    }
}
