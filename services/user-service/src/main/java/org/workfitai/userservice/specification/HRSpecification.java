package org.workfitai.userservice.specification;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.workfitai.userservice.model.HREntity;

import java.util.ArrayList;
import java.util.List;

@Component
public class HRSpecification {

  /**
   * Build a JPA Specification with optional company scope + keyword search.
   *
   * @param keyword   case-insensitive substring match across name / email / dept / address;
   *                  null or blank = no keyword filter
   * @param companyNo when non-null, restricts results to HRs belonging to this company
   *                  (used when the caller is HR_MANAGER)
   */
  public Specification<HREntity> filter(String keyword, String companyNo) {
    return (root, query, cb) -> {
      List<Predicate> conditions = new ArrayList<>();

      // Company scope — applied for HR_MANAGER callers
      if (companyNo != null && !companyNo.isBlank()) {
        conditions.add(cb.equal(root.get("companyNo"), companyNo));
      }

      // Keyword search
      if (keyword != null && !keyword.trim().isEmpty()) {
        String like = "%" + keyword.toLowerCase() + "%";
        conditions.add(cb.or(
            cb.like(cb.lower(root.get("fullName")), like),
            cb.like(cb.lower(root.get("email")), like),
            cb.like(cb.lower(root.get("department")), like),
            cb.like(cb.lower(root.get("address")), like)
        ));
      }

      return cb.and(conditions.toArray(new Predicate[0]));
    };
  }
}
