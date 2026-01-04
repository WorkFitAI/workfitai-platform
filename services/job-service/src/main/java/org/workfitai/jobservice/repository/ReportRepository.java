package org.workfitai.jobservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.workfitai.jobservice.model.Report;
import org.workfitai.jobservice.model.enums.EReportStatus;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReportRepository extends JpaRepository<Report, UUID>, JpaSpecificationExecutor<Report> {
    boolean existsByJob_JobIdAndCreatedBy(UUID jobId, String createdBy);

    @Modifying
    @Query("""
                UPDATE Report r
                SET r.status = :newStatus
                WHERE r.job.jobId = :jobId
                  AND r.status <> :newStatus
                  AND r.status IN :allowedCurrentStatuses
            """)
    int updateStatusWithWorkflow(
            @Param("jobId") UUID jobId,
            @Param("newStatus") EReportStatus newStatus,
            @Param("allowedCurrentStatuses") List<EReportStatus> allowedCurrentStatuses
    );

}
