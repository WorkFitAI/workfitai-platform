package org.workfitai.applicationservice.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.workfitai.applicationservice.client.CvServiceClient;
import org.workfitai.applicationservice.client.RecommendationEngineClient;
import org.workfitai.applicationservice.dto.CvSnapshotPushRequest;
import org.workfitai.applicationservice.dto.CvSnapshotResponse;
import org.workfitai.applicationservice.dto.ReparseSnapshotRequest;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.repository.ApplicationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Safety net for applications whose SNAPSHOT_CV saga step failed at apply time
 * (cv-service was unavailable/timed out — best-effort, swallowed by the saga).
 *
 * Without this job, an applicant whose snapshot creation failed once stays
 * permanently invisible to CV ranking: there is nothing for recommendation-engine
 * to fetch (cvSnapshotId is null), and nothing re-triggers the original upload flow.
 *
 * Runs periodically: finds active applications with cvSnapshotId == null, asks
 * cv-service to re-parse from the already-stored cvFileUrl (no re-upload needed —
 * the original PDF is independent of whether the snapshot step ever succeeded),
 * then pushes the result straight into recommendation-engine's CvReferStore so the
 * fix is visible immediately instead of waiting for the next full resync.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CvSnapshotReconciliationService {

    private static final List<ApplicationStatus> POOL_STATUSES = List.of(
            ApplicationStatus.APPLIED,
            ApplicationStatus.REVIEWING,
            ApplicationStatus.INTERVIEW);

    /** Cap per tick so a large backlog doesn't hammer cv-service in one burst. */
    private static final int MAX_PER_TICK = 25;

    private final ApplicationRepository applicationRepository;
    private final CvServiceClient cvServiceClient;
    private final RecommendationEngineClient recommendationEngineClient;

    @Value("${app.reconciliation.cv-snapshot.enabled:true}")
    private boolean enabled;

    @Scheduled(
            initialDelayString = "${app.reconciliation.cv-snapshot.initial-delay-ms:60000}",
            fixedDelayString = "${app.reconciliation.cv-snapshot.interval-ms:600000}")
    public void reconcileMissingSnapshots() {
        if (!enabled) {
            return;
        }

        List<Application> candidates = applicationRepository
                .findByCvSnapshotIdIsNullAndStatusInAndDeletedAtIsNull(POOL_STATUSES);

        if (candidates.isEmpty()) {
            return;
        }

        log.info("CvSnapshotReconciliation: {} application(s) missing a CV snapshot, processing up to {}",
                candidates.size(), MAX_PER_TICK);

        int fixed = 0;
        for (Application app : candidates.subList(0, Math.min(MAX_PER_TICK, candidates.size()))) {
            if (reconcileOne(app)) {
                fixed++;
            }
        }

        log.info("CvSnapshotReconciliation: fixed {}/{} application(s) this tick",
                fixed, Math.min(MAX_PER_TICK, candidates.size()));
    }

    private boolean reconcileOne(Application app) {
        try {
            CvSnapshotResponse snapshot = cvServiceClient.reparseSnapshot(
                    ReparseSnapshotRequest.builder()
                            .username(app.getUsername())
                            .applicationId(app.getId())
                            .jobName(app.getJobSnapshot() != null ? app.getJobSnapshot().getTitle() : "cv")
                            .cvFileUrl(app.getCvFileUrl())
                            .build());

            app.setCvSnapshotId(snapshot.getCvId());
            applicationRepository.save(app);

            try {
                recommendationEngineClient.pushCvSnapshot(
                        CvSnapshotPushRequest.builder()
                                .jobId(app.getJobId())
                                .username(app.getUsername())
                                .summary(snapshot.getSummary())
                                .experience(snapshot.getExperience())
                                .skills(snapshot.getSkills())
                                .education(snapshot.getEducation())
                                .build());
            } catch (Exception pushEx) {
                // Non-fatal: cvSnapshotId is already persisted, so the next full
                // recommendation-engine resync (or the next reconciliation tick) will pick it up.
                log.warn("CvSnapshotReconciliation: reparsed applicationId={} but failed to push to "
                        + "recommendation-engine (will surface on next resync): {}",
                        app.getId(), pushEx.getMessage());
            }

            log.info("CvSnapshotReconciliation: backfilled applicationId={} username={} cvId={}",
                    app.getId(), app.getUsername(), snapshot.getCvId());
            return true;
        } catch (Exception e) {
            log.warn("CvSnapshotReconciliation: reparse still failing for applicationId={} username={}: {}",
                    app.getId(), app.getUsername(), e.getMessage());
            return false;
        }
    }
}
