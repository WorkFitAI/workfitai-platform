package org.workfitai.jobservice.service.specifications;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.workfitai.jobservice.model.Job;
import org.workfitai.jobservice.model.enums.ExperienceLevel;
import org.workfitai.jobservice.model.enums.JobStatus;
import org.workfitai.jobservice.security.SecurityUtils;

import jakarta.persistence.criteria.Join;

public class JobSpecifications {

    public static Specification<Job> statusPublished() {
        return (root, query, cb) -> cb.equal(root.get("status"), JobStatus.PUBLISHED);
    }

    public static Specification<Job> statusClosed() {
        return (root, query, cb) -> cb.equal(root.get("status"), JobStatus.CLOSED);
    }

    public static Specification<Job> statusIn(List<JobStatus> statuses) {
        return (root, query, cb) -> root.get("status").in(statuses);
    }

    public static Specification<Job> isNotStatusDraft() {
        return (root, query, cb) -> cb.notEqual(root.get("status"), JobStatus.DRAFT);
    }

    public static Specification<Job> ownedByCurrentUser() {
        return (root, query, cb) -> cb.equal(root.get("createdBy"), SecurityUtils.getCurrentUser());
    }

    public static Specification<Job> isNoDeleted() {
        return (root, query, cb) -> cb.equal(root.get("isDeleted"), false);
    }

    public static Specification<Job> hasCompanyId(String companyId) {
        return (root, query, cb) -> cb.equal(root.get("company").get("companyNo"), companyId);
    }

    public static Specification<Job> notJobId(UUID jobId) {
        return (root, query, cb) -> cb.notEqual(root.get("jobId"), jobId);
    }

    public static Specification<Job> hasSkills(List<UUID> skillIds) {
        return (root, query, cb) -> {
            Join<Job, Object> skills = root.join("skills");
            query.distinct(true);
            return skills.get("skillId").in(skillIds);
        };
    }

    public static Specification<Job> hasLocation(String location) {
        return (root, query, cb) -> cb.equal(cb.lower(root.get("location")), location.toLowerCase());
    }

    public static Specification<Job> hasTitleLike(String keyword) {
        return (root, query, cb) -> cb.like(cb.lower(root.get("title")), "%" + keyword.toLowerCase() + "%");
    }

    public static Specification<Job> hasExperience(ExperienceLevel exp) {
        return (root, query, cb) -> cb.equal(root.get("experienceLevel"), exp);
    }

    /*
     * SELECT DISTINCT j.*
     * FROM job j
     * LEFT JOIN job_skill js ON j.job_id = js.job_id
     * LEFT JOIN skill s ON js.skill_id = s.skill_id
     * WHERE j.job_id <> ?
     * AND j.is_deleted = false
     * AND j.status <> 'DRAFT'
     * AND (
     * s.skill_id IN (?, ?, ...)
     * OR LOWER(j.title) LIKE LOWER('%keyword%')
     * )
     * AND (
     * LOWER(j.location) = LOWER(?)
     * OR j.experience_level = ?
     * )
     */

    public static Specification<Job> similarJobs(
            UUID jobId,
            List<UUID> skillIds,
            String location,
            String keyword,
            ExperienceLevel exp) {

        return Specification.where(notJobId(jobId))
                .and(isNoDeleted())
                .and(isNotStatusDraft())
                .and(
                        hasSkills(skillIds)
                                .or(hasTitleLike(keyword)))
                .and(
                        hasLocation(location)
                                .or(hasExperience(exp)));
    }

    public static Specification<Job> isActive() {
        return (root, query, cb) -> cb.greaterThan(root.get("expiresAt"), cb.currentTimestamp());
    }

    public static Specification<Job> featuredJobs() {
        return Specification.where(isNoDeleted())
                .and(isNotStatusDraft());
    }
}
