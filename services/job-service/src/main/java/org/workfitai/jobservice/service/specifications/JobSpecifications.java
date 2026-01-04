package org.workfitai.jobservice.service.specifications;

import org.springframework.data.jpa.domain.Specification;
import org.workfitai.jobservice.model.Job;
import org.workfitai.jobservice.model.enums.JobStatus;
import org.workfitai.jobservice.security.SecurityUtils;

public class JobSpecifications {

    public static Specification<Job> statusPublished() {
        return (root, query, cb) ->
                cb.equal(root.get("status"), JobStatus.PUBLISHED);
    }

    public static Specification<Job> ownedByCurrentUser() {
        return (root, query, cb) ->
                cb.equal(root.get("createdBy"), SecurityUtils.getCurrentUser());
    }

    public static Specification<Job> isNoDeleted() {
        return (root, query, cb) ->
                cb.equal(root.get("isDeleted"), false);
    }

    public static Specification<Job> hasCompanyId(String companyId) {
        return (root, query, cb) ->
                cb.equal(root.get("company").get("id"), companyId);
    }
}

