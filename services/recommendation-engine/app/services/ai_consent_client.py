"""
Fetch a candidate's personal AI job-recommendation consent from user-service.

Only meaningful for endpoints that carry a candidate identity (e.g.
by-profile with an optional candidateUsername) - by-resume/similar-jobs/search
have no caller-supplied identity today, so only the admin toggle applies there.

Fails CLOSED (treats consent as NOT granted) on any error. This is a consent
gate for AI processing of personal data - the platform default is opt-in
(disabled until explicitly enabled), so an inability to confirm consent must
not be silently treated as consent. This is the opposite of the fail-open
precedent used for HR notification-preference lookups elsewhere in this
codebase, which gate non-sensitive operational emails, not consent.
"""

import logging
from typing import Optional

import httpx

logger = logging.getLogger(__name__)


async def fetch_ai_job_recommendation_consent(
    user_service_url: str, username: str, timeout: int
) -> bool:
    url = f"{user_service_url}/api/v1/internal/ai-job-recommendation-settings/{username}"
    try:
        async with httpx.AsyncClient(timeout=timeout) as client:
            resp = await client.get(url)
            resp.raise_for_status()
            body = resp.json()
            enabled: Optional[bool] = body.get("aiJobRecommendationEnabled")
            return enabled is None or enabled
    except Exception as exc:
        logger.error(
            "Failed to fetch AI job-recommendation consent for %s from %s: %s - failing closed",
            username, url, exc,
        )
        return False
