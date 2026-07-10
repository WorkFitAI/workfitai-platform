"""
Initial sync - fetch jobs from Job Service and build FAISS index
"""

import asyncio
import logging
import httpx
from typing import Dict

from app.config import get_settings
from app.services import job_formatter
from app.services.field_format import JOB_FIELDS

logger = logging.getLogger(__name__)

_MAX_SYNC_RETRIES = 5
_RETRY_BASE_DELAY = 10  # seconds; multiplied by attempt number


def _normalize_job_data(job: Dict) -> Dict:
    """
    Normalize job data from Job Service API to internal format.

    Maps API fields to expected format:
    - postId -> id
    - skillNames -> skills
    - company.name -> company.companyName

    shortDescription is kept under its own key (not renamed to description) --
    job_formatter.format_job_as_fields()/format_job_as_text() treat
    shortDescription and description as two distinct sections (jd_overview vs.
    the full-text field), and _fetch_job_detail_fields separately supplies the
    real description below. Collapsing them here previously caused every
    initial-synced job to silently lose its jd_overview field.
    """
    return {
        "id": job.get("postId"),
        "title": job.get("title"),
        "shortDescription": job.get("shortDescription"),
        "location": job.get("company", {}).get("address", ""),
        "employmentType": job.get("employmentType"),
        "experienceLevel": job.get("experienceLevel"),
        "salaryMin": job.get("salaryMin"),
        "salaryMax": job.get("salaryMax"),
        "skills": job.get("skillNames", []),
        "company": {
            "companyId": job.get("company", {}).get("companyNo", ""),
            "companyName": job.get("company", {}).get("name", ""),
            "companySize": job.get("company", {}).get("size"),
        },
        "expiresAt": job.get("expiresAt"),
        "createdDate": job.get("createdDate"),
    }


async def _fetch_job_detail_fields(client: httpx.AsyncClient, job_service_url: str, job_id: str) -> Dict:
    """
    Fetch requirements/responsibilities/benefits/description from the
    per-job detail endpoint.

    The paginated /public/jobs list response (used for everything else
    _normalize_job_data maps) doesn't carry these fields -- only the fuller
    single-job detail response does (same endpoint app/services/cv_rank_assembly.py's
    fetch_job_data already relies on for the same fields). Without this, a
    job indexed during initial sync would get a degraded (1-of-5-field)
    multi-field embedding while the same job re-synced later via a Kafka
    job.updated event -- whose payload DOES carry these fields -- would not,
    an inconsistency between "just started" and "steady state" index quality.

    Failure is non-fatal: returns {} and the job still gets embedded from
    whatever fields normalization already provided (the multi-field encoder
    already tolerates missing fields).
    """
    try:
        resp = await client.get(f"{job_service_url}/public/jobs/{job_id}")
        resp.raise_for_status()
        body = resp.json()
        detail = body.get("data", body)
        return {
            "description": detail.get("description", ""),
            "requirements": detail.get("requirements", ""),
            "responsibilities": detail.get("responsibilities", ""),
            "benefits": detail.get("benefits", ""),
        }
    except Exception as e:
        logger.warning(f"Failed to fetch job detail for {job_id}: {e}")
        return {}


async def sync_jobs_from_service(faiss_manager, embedding_generator) -> int:
    """
    Fetch all active jobs from Job Service and build FAISS index.

    Retries up to _MAX_SYNC_RETRIES times on connection errors to handle
    cases where job-service is still starting when this runs.

    Returns:
        Number of jobs synced
    """
    settings = get_settings()

    for attempt in range(1, _MAX_SYNC_RETRIES + 1):
        try:
            return await _run_sync(settings, faiss_manager, embedding_generator)
        except httpx.ConnectError as e:
            if attempt < _MAX_SYNC_RETRIES:
                delay = _RETRY_BASE_DELAY * attempt
                logger.warning(
                    f"Job Service not ready (attempt {attempt}/{_MAX_SYNC_RETRIES}), "
                    f"retrying in {delay}s: {e}"
                )
                await asyncio.sleep(delay)
            else:
                logger.error(f"Job Service unreachable after {_MAX_SYNC_RETRIES} attempts, sync skipped")
                return 0

    return 0


async def _run_sync(settings, faiss_manager, embedding_generator) -> int:
    logger.info("Starting initial job sync from Job Service...")
    logger.info(f"Job Service URL: {settings.JOB_SERVICE_URL}")

    synced_count = 0
    page = 0
    page_size = 50

    async with httpx.AsyncClient(timeout=settings.JOB_SERVICE_TIMEOUT) as client:
        while True:
            try:
                # Call job-service directly — /public/jobs is the controller path (/job prefix is stripped by API Gateway)
                url = f"{settings.JOB_SERVICE_URL}/public/jobs"
                params = {"page": page, "size": page_size}

                logger.info(f"Fetching page {page} (size={page_size})...")
                response = await client.get(url, params=params)
                response.raise_for_status()

                data = response.json()

                jobs = data.get("data", {}).get("result", [])
                meta = data.get("data", {}).get("meta", {})

                if not jobs:
                    logger.info(f"No more jobs found on page {page}")
                    break

                logger.info(f"Processing {len(jobs)} jobs from page {page}...")

                for job in jobs:
                    try:
                        job_id = job.get("postId")
                        if not job_id:
                            logger.warning(f"Job missing postId, skipping: {job}")
                            continue

                        normalized_job = _normalize_job_data(job)
                        detail_fields = await _fetch_job_detail_fields(client, settings.JOB_SERVICE_URL, job_id)
                        normalized_job.update(detail_fields)

                        fields = job_formatter.format_job_as_fields(normalized_job)
                        pooled_embedding, per_field_embeddings, mask = embedding_generator.encode_job_fields(
                            fields, JOB_FIELDS
                        )
                        field_mask = dict(zip(JOB_FIELDS, mask))
                        faiss_manager.add_job_with_embedding(
                            job_id, pooled_embedding, normalized_job,
                            field_embeddings=per_field_embeddings, field_mask=field_mask,
                        )
                        synced_count += 1

                    except Exception as e:
                        logger.error(f"Error processing job {job.get('id')}: {e}")
                        continue

                logger.info(f"✓ Synced {len(jobs)} jobs from page {page} (total: {synced_count})")

                current_page = meta.get("page", page + 1)
                total_pages = meta.get("pages", 1)

                if current_page >= total_pages:
                    logger.info(f"Reached last page: {current_page}/{total_pages}")
                    break

                page += 1

            except httpx.ConnectError:
                raise  # bubble up to retry loop in sync_jobs_from_service
            except httpx.HTTPStatusError as e:
                logger.error(f"HTTP error on page {page}: {e.response.status_code} - {e.response.text}")
                break
            except httpx.TimeoutException:
                logger.error(f"Timeout fetching page {page}")
                break
            except Exception as e:
                logger.error(f"Error fetching page {page}: {e}")
                break

    logger.info(f"✓ Initial sync complete: {synced_count} jobs indexed")
    return synced_count
