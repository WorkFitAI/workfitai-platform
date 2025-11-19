package org.workfitai.userservice.specification;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.workfitai.userservice.model.AdminEntity;

@Component
public class AdminSpecification {
  public Specification<AdminEntity> filter(String keyword) {
    return (root, query, cb) -> {
      if (keyword == null || keyword.trim().isEmpty()) {
        return cb.conjunction();
      }
      String like = "%" + keyword.toLowerCase() + "%";

      Predicate p1 = cb.like(cb.lower(root.get("fullName")), like);
      Predicate p2 = cb.like(cb.lower(root.get("email")), like);

      return cb.or(p1, p2);
    };
  }
}
