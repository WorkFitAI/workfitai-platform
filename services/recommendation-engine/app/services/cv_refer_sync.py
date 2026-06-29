"""
Initial sync for CvReferStore on startup.

Fetches the active application pool from application-service to populate
job_applicants (job → set of usernames).

CV snapshot fields are NOT stored directly in the active-pool response (to avoid
document bloat in application-service). Instead:
  1. We collect all cvSnapshotIds from the pool response.
  2. We call cv-service POST /internal/cvs/batch-by-application-ids to fetch
     the structured fields in a single batch request.
  3. We index the snapshot results by cvSnapshotId for efficient lookup.

If cv-service is unavailable, cv_data remains empty and ranking will still work
(applicants will appear in results but with empty CV fields / lower scores).

Retries on ConnectError (services may still be starting when this runs).
"""

import asyncio
import logging
from typing import Dict, List, Optional

import httpx

from app.services.cv_refer_store import CvReferStore

logger = logging.getLogger(__name__)

_MAX_RETRIES = 5
_RETRY_BASE_DELAY = 10  # seconds; multiplied by attempt number


async def sync_cv_refer_from_services(store: CvReferStore, settings) -> None:
    for attempt in range(1, _MAX_RETRIES + 1):
        try:
            await _run_sync(store, settings)
            return
        except httpx.ConnectError as e:
            if attempt < _MAX_RETRIES:
                delay = _RETRY_BASE_DELAY * attempt
                logger.warning(
                    "cv-refer sync: application-service not ready (attempt %d/%d), retrying in %ds: %s",
                    attempt, _MAX_RETRIES, delay, e,
                )
                await asyncio.sleep(delay)
            else:
                logger.error("cv-refer sync: application-service unreachable after %d attempts, skipped", _MAX_RETRIES)


async def _run_sync(store: CvReferStore, settings) -> None:
    pool_url = f"{settings.APPLICATION_SERVICE_URL}/internal/applications/active-pool"
    logger.info("cv-refer sync: fetching active pool from %s", pool_url)

    async with httpx.AsyncClient(timeout=settings.INTERNAL_SERVICE_TIMEOUT) as client:
        try:
            resp = await client.get(pool_url)
            resp.raise_for_status()
        except httpx.ConnectError:
            raise
        except httpx.HTTPStatusError as e:
            logger.error("cv-refer sync: active-pool returned %d — %s", e.response.status_code, e.response.text)
            return
        except Exception as e:
            logger.error("cv-refer sync: failed to fetch active-pool: %s", e)
            return

        pool: list[dict] = resp.json()
        if not pool:
            logger.info("cv-refer sync: no active applications found, store remains empty")
            return

        # --- Phase 1: Populate applicant pools + collect cvSnapshotIds ---
        total_applicants = 0
        # Mapping: cvSnapshotId → (job_id, username) so we can set snapshots after batch fetch
        snapshot_id_to_applicant: Dict[str, tuple] = {}

        for entry in pool:
            job_id = entry.get("jobId")
            applicants = entry.get("applicants", [])
            if not job_id or not applicants:
                continue
            for applicant in applicants:
                username = applicant.get("username")
                if not username:
                    continue
                store.add_applicant(job_id, username)
                total_applicants += 1

                cv_snapshot_id = applicant.get("cvSnapshotId")
                if cv_snapshot_id:
                    snapshot_id_to_applicant[cv_snapshot_id] = (job_id, username)

    logger.info(
        "cv-refer sync phase 1: %d applicants added across %d jobs, %d have cvSnapshotIds",
        total_applicants, len(pool), len(snapshot_id_to_applicant),
    )

    # --- Phase 2: Batch-fetch CV snapshots from cv-service ---
    if snapshot_id_to_applicant:
        await _fetch_and_store_cv_snapshots(
            store,
            settings,
            list(snapshot_id_to_applicant.keys()),
            snapshot_id_to_applicant,
        )

    stats = store.get_stats()
    logger.info(
        "cv-refer sync complete: %d jobs tracked, %d CV snapshots loaded",
        stats["total_jobs_tracked"], stats["total_cv_profiles"],
    )


async def _fetch_and_store_cv_snapshots(
    store: CvReferStore,
    settings,
    application_ids: List[str],
    id_to_applicant: Dict[str, tuple],
) -> None:
    """
    Batch-fetch CV snapshot data from cv-service and populate the store.
    Failures are non-fatal: cv_data will be empty for missing snapshots.
    """
    cv_service_url = getattr(settings, "CV_SERVICE_URL", None)
    if not cv_service_url:
        logger.warning(
            "cv-refer sync: CV_SERVICE_URL not configured — skipping cv snapshot batch fetch. "
            "Ranking will work but CV fields will be empty."
        )
        return

    batch_url = f"{cv_service_url}/internal/cvs/batch-by-application-ids"
    logger.info(
        "cv-refer sync: fetching %d CV snapshots from %s", len(application_ids), batch_url
    )

    try:
        async with httpx.AsyncClient(timeout=settings.INTERNAL_SERVICE_TIMEOUT) as client:
            resp = await client.post(batch_url, json=application_ids)
            resp.raise_for_status()
            snapshots: List[dict] = resp.json()

        loaded = 0
        for snap in snapshots:
            cv_snapshot_id = snap.get("cvId")
            if not cv_snapshot_id or cv_snapshot_id not in id_to_applicant:
                continue
            job_id, username = id_to_applicant[cv_snapshot_id]
            cv_data = {
                "summary":    snap.get("summary", ""),
                "experience": snap.get("experience", ""),
                "skills":     snap.get("skills", ""),
                "education":  snap.get("education", ""),
            }
            # Always store (mirrors CvReferConsumer) — applicant still reaches the
            # ranking pipeline even with an empty/partial snapshot.
            store.set_cv_snapshot(job_id, username, cv_data)
            loaded += 1

        logger.info(
            "cv-refer sync phase 2: loaded %d/%d CV snapshots from cv-service",
            loaded, len(application_ids),
        )

    except Exception as e:
        logger.warning(
            "cv-refer sync: failed to fetch CV snapshots from cv-service (non-fatal): %s. "
            "Ranking will work but CV fields will be empty for cold-start applicants.",
            e,
        )
