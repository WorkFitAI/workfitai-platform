package org.workfitai.userservice.specification;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;
import org.workfitai.userservice.model.CandidateEntity;

public class CandidateSpecification {

  public static Specification<CandidateEntity> search(String keyword) {
    return (root, query, cb) -> {
      if (!StringUtils.hasText(keyword)) return cb.conjunction();
      String pattern = "%" + keyword.toLowerCase() + "%";
      Predicate name = cb.like(cb.lower(root.get("fullName")), pattern);
      Predicate education = cb.like(cb.lower(root.get("education")), pattern);
      Predicate position = cb.like(cb.lower(root.get("expectedPosition")), pattern);
      return cb.or(name, education, position);
    };
  }

  public static Specification<CandidateEntity> filter(String education, Integer minExp, Integer maxExp) {
    return (root, query, cb) -> {
      Predicate predicate = cb.conjunction();

      if (StringUtils.hasText(education)) {
        predicate = cb.and(predicate,
            cb.like(cb.lower(root.get("education")), "%" + education.toLowerCase() + "%"));
      }

      if (minExp != null) {
        predicate = cb.and(predicate, cb.greaterThanOrEqualTo(root.get("totalExperience"), minExp));
      }

      if (maxExp != null) {
        predicate = cb.and(predicate, cb.lessThanOrEqualTo(root.get("totalExperience"), maxExp));
      }

      return predicate;
    };
  }
}
