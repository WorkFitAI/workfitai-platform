package org.workfitai.userservice.specification;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.workfitai.userservice.model.HREntity;

@Component
public class HRSpecification {

  public Specification<HREntity> filter(String keyword) {
    return (root, query, cb) -> {
      if (keyword == null || keyword.trim().isEmpty()) {
        return cb.conjunction();
      }
      String like = "%" + keyword.toLowerCase() + "%";

      Predicate p1 = cb.like(cb.lower(root.get("fullName")), like);
      Predicate p2 = cb.like(cb.lower(root.get("email")), like);
      Predicate p3 = cb.like(cb.lower(root.get("department")), like);
      Predicate p4 = cb.like(cb.lower(root.get("address")), like);

      return cb.or(p1, p2, p3, p4);
    };
  }
}
