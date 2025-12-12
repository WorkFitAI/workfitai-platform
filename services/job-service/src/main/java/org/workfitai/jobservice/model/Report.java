package org.workfitai.jobservice.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.workfitai.jobservice.model.enums.EReportStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "reports")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Report extends AbstractAuditingEntity<UUID> {
    @Id
    @GeneratedValue
    @Column(name = "report_id", updatable = false, nullable = false)
    private UUID reportId;

    @Column(columnDefinition = "TEXT")
    private String reportContent;

    @NotNull(message = "Report status must not be null")
    @Enumerated(EnumType.STRING)
    private EReportStatus status;

    @ElementCollection
    @CollectionTable(name = "report_images", joinColumns = @JoinColumn(name = "report_id"))
    @Column(name = "image_url")
    private List<String> images = new ArrayList<>();

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    @NotNull(message = "Job must not be null")
    private Job job;

    @Override
    public UUID getId() {
        return reportId;
    }

    @PrePersist
    public void init() {
        if (status == null) status = EReportStatus.PENDING;
    }
}
