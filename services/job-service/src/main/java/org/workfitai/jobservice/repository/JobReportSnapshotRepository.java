package org.workfitai.jobservice.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import org.workfitai.jobservice.model.JobReportSnapshot;

@Repository
public interface JobReportSnapshotRepository
        extends JpaRepository<JobReportSnapshot, UUID>, JpaSpecificationExecutor<JobReportSnapshot> {
    List<JobReportSnapshot> findAllByReport_ReportIdIn(
            Collection<UUID> reportIds);

}
