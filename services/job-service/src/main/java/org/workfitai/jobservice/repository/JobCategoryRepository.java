package org.workfitai.jobservice.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import org.workfitai.jobservice.model.JobCategory;

@Repository
public interface JobCategoryRepository extends JpaRepository<JobCategory, UUID>, JpaSpecificationExecutor<JobCategory> {

}
