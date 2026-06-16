"""
API route handlers for recommendation endpoints
"""

import base64
import time
import logging
from typing import List, Dict, Optional

import anyio
from fastapi import APIRouter, HTTPException, Request, status

from app.models.requests import (
    RecommendByResumeRequest,
    RecommendByProfileRequest,
    SimilarJobsRequest,
    SemanticSearchRequest
)
from app.models.responses import (
    RecommendationResponse,
    RecommendationData,
    JobRecommendation
)

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/v1/recommendations", tags=["Recommendations"])


def _extract_company_name(result: Dict) -> str:
    """Extract company name from result metadata"""
    company = result.get('company')
    if isinstance(company, dict):
        return company.get('companyName', 'Unknown')
    elif isinstance(company, str):
        return company
    return 'Unknown'


def _prepare_filters(request_filters) -> Dict:
    """Convert request filters to FAISS search filters"""
    filters = {}
    if not request_filters:
        return filters

    if request_filters.locations and len(request_filters.locations) > 0:
        filters['location'] = request_filters.locations[0]
    if request_filters.minSalary:
        filters['minSalary'] = request_filters.minSalary
    if request_filters.maxSalary:
        filters['maxSalary'] = request_filters.maxSalary
    if request_filters.experienceLevels and len(request_filters.experienceLevels) > 0:
        filters['experienceLevel'] = request_filters.experienceLevels[0]
    if request_filters.employmentTypes and len(request_filters.employmentTypes) > 0:
        filters['jobType'] = request_filters.employmentTypes[0]

    return filters


def _create_job_recommendation(result: Dict, rank: int) -> JobRecommendation:
    """Create JobRecommendation from FAISS search result"""
    score = min(1.0, max(0.0, float(result['score'])))
    return JobRecommendation(
        jobId=result['jobId'],
        score=score,
        rank=rank,
        title=result.get('title', 'Unknown'),
        company=_extract_company_name(result),
        location=result.get('location', 'Unknown'),
        salary=result.get('salary'),
        experienceLevel=result.get('experienceLevel'),
        jobType=result.get('jobType'),
        skills=result.get('skills', [])
    )


# ---------------------------------------------------------------------------
# by-resume
# ---------------------------------------------------------------------------

@router.post("/by-resume", response_model=RecommendationResponse)
async def recommend_by_resume(request: RecommendByResumeRequest, req: Request):
    """
    Get job recommendations based on resume PDF.

    - **resumeFile**: Base64 encoded PDF file
    - **topK**: Number of recommendations (1-100)
    - **filters**: Optional filters for location, salary, etc.

    Cache key: SHA-256 of (formatted resume text, filters, topK).
    PDF parsing runs before cache check; embedding+FAISS is cached.
    """
    from app.config import get_settings
    from app.services.recommendation_cache import RecommendationCache, get_recommendation_cache

    start_time = time.time()
    settings = get_settings()

    faiss_manager = req.app.state.faiss_manager()
    embedding_generator = req.app.state.embedding_generator()
    resume_parser = req.app.state.resume_parser()

    if not faiss_manager or not embedding_generator or not resume_parser:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Service not ready")

    logger.info("Recommend by resume (base64 length: %d, topK=%d)", len(request.resumeFile), request.topK)

    try:
        pdf_bytes = base64.b64decode(request.resumeFile)
    except Exception as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=f"Invalid base64 encoding: {exc}")

    try:
        parsed_data = resume_parser.parse_resume(pdf_bytes)
        if not parsed_data:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Failed to parse resume")
    except HTTPException:
        raise
    except Exception as exc:
        logger.error("Resume parsing error: %s", exc, exc_info=True)
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=f"Resume parsing failed: {exc}")

    # Key computed after parse (cache saves embedding + FAISS, not PDF parse)
    formatted_text = resume_parser.format_resume_for_matching(parsed_data)
    filters = _prepare_filters(request.filters)

    def _compute() -> List[JobRecommendation]:
        query_embedding = embedding_generator.encode_resume(formatted_text)
        results = faiss_manager.search(query_embedding=query_embedding, top_k=request.topK, filters=filters)
        return [_create_job_recommendation(r, i + 1) for i, r in enumerate(results)]

    if settings.RECOMMENDATION_CACHE_ENABLED:
        cache = get_recommendation_cache()
        key = RecommendationCache.key_for_resume(formatted_text, filters, request.topK)
        cooldown = float(settings.RECOMMENDATION_CACHE_COOLDOWN_SECONDS)
        recommendations, was_hit = await anyio.to_thread.run_sync(
            lambda: cache.get_or_compute(key, _compute, cooldown)
        )
        cache.maybe_refresh(key, _compute, cooldown)
        logger.info("by-resume: cache=%s results=%d", "hit" if was_hit else "miss", len(recommendations))
    else:
        recommendations = await anyio.to_thread.run_sync(_compute)

    processing_time = f"{int((time.time() - start_time) * 1000)}ms"
    logger.info("✓ Found %d matching jobs in %s", len(recommendations), processing_time)

    return RecommendationResponse(
        success=True,
        data=RecommendationData(
            totalResults=len(recommendations),
            recommendations=recommendations,
            processingTime=processing_time
        ),
        message=f"Found {len(recommendations)} matching jobs"
    )


# ---------------------------------------------------------------------------
# by-profile
# ---------------------------------------------------------------------------

@router.post("/by-profile", response_model=RecommendationResponse)
async def recommend_by_profile(request: RecommendByProfileRequest, req: Request):
    """
    Get job recommendations based on text profile.

    Pipeline:
    1. Bi-encoder retrieves top-K candidates from FAISS (fast)
    2. Cross-encoder reranks to top-N (accurate) — if enabled

    - **profileText**: User profile text (skills, experience)
    - **topK**: Number of final recommendations (default: 20)
    - **filters**: Optional filters
    """
    from app.config import get_settings
    from app.services.recommendation_cache import RecommendationCache, get_recommendation_cache

    start_time = time.time()
    settings = get_settings()

    faiss_manager = req.app.state.faiss_manager()
    embedding_generator = req.app.state.embedding_generator()
    reranker = req.app.state.reranker()

    if not faiss_manager or not embedding_generator:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Service not ready")

    logger.info("Recommend by profile (text length: %d, topK=%d)", len(request.profileText), request.topK)

    filters = _prepare_filters(request.filters)
    retrieve_k = settings.RERANK_TOP_K if reranker else request.topK

    def _compute() -> List[JobRecommendation]:
        query_embedding = embedding_generator.encode_resume(request.profileText)
        results = faiss_manager.search(query_embedding=query_embedding, top_k=retrieve_k, filters=filters)
        candidates = [_create_job_recommendation(r, i + 1) for i, r in enumerate(results)]

        if reranker and candidates:
            try:
                candidates_dict = [c.model_dump() if hasattr(c, 'model_dump') else c for c in candidates]
                reranked = reranker.rerank(
                    resume_text=request.profileText,
                    candidates=candidates_dict,
                    top_n=request.topK
                )
                return [JobRecommendation(**r) for r in reranked]
            except Exception as exc:
                logger.error("Reranking failed, using bi-encoder results: %s", exc)
                return candidates[:request.topK]
        return candidates[:request.topK]

    if settings.RECOMMENDATION_CACHE_ENABLED:
        cache = get_recommendation_cache()
        key = RecommendationCache.key_for_profile(request.profileText, filters, request.topK)
        cooldown = float(settings.RECOMMENDATION_CACHE_COOLDOWN_SECONDS)
        recommendations, was_hit = await anyio.to_thread.run_sync(
            lambda: cache.get_or_compute(key, _compute, cooldown)
        )
        cache.maybe_refresh(key, _compute, cooldown)
        logger.info("by-profile: cache=%s results=%d", "hit" if was_hit else "miss", len(recommendations))
    else:
        recommendations = await anyio.to_thread.run_sync(_compute)

    processing_time = f"{int((time.time() - start_time) * 1000)}ms"
    logger.info("✓ Found %d matching jobs in %s", len(recommendations), processing_time)

    return RecommendationResponse(
        success=True,
        data=RecommendationData(
            totalResults=len(recommendations),
            recommendations=recommendations,
            processingTime=processing_time
        ),
        message=f"Found {len(recommendations)} matching jobs"
    )


# ---------------------------------------------------------------------------
# similar-jobs
# ---------------------------------------------------------------------------

@router.post("/similar-jobs", response_model=RecommendationResponse)
async def find_similar_jobs(request: SimilarJobsRequest, req: Request):
    """
    Find jobs similar to a reference job.

    - **jobId**: Reference job ID
    - **topK**: Number of similar jobs
    - **excludeSameCompany**: Exclude jobs from same company
    """
    from app.config import get_settings
    from app.services.recommendation_cache import RecommendationCache, get_recommendation_cache

    start_time = time.time()
    settings = get_settings()

    faiss_manager = req.app.state.faiss_manager()
    if not faiss_manager:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Service not ready")

    logger.info(
        "Finding similar jobs for %s (topK=%d, excludeSameCompany=%s)",
        request.jobId, request.topK, request.excludeSameCompany
    )

    # 404 guard before any cache check
    job_data = faiss_manager.get_job_by_id(request.jobId)
    if not job_data:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Job {request.jobId} not found in index"
        )

    reference_company = _extract_company_name(job_data)
    query_embedding = job_data['embedding']

    def _compute() -> List[JobRecommendation]:
        results = faiss_manager.search(
            query_embedding=query_embedding,
            top_k=request.topK + 1,
            filters={}
        )
        recommendations: List[JobRecommendation] = []
        for result in results:
            if result['jobId'] == request.jobId:
                continue
            if request.excludeSameCompany and _extract_company_name(result) == reference_company:
                continue
            recommendations.append(_create_job_recommendation(result, len(recommendations) + 1))
            if len(recommendations) >= request.topK:
                break
        return recommendations

    if settings.RECOMMENDATION_CACHE_ENABLED:
        cache = get_recommendation_cache()
        key = RecommendationCache.key_for_similar(request.jobId, request.topK, request.excludeSameCompany)
        cooldown = float(settings.RECOMMENDATION_CACHE_COOLDOWN_SECONDS)
        recommendations, was_hit = await anyio.to_thread.run_sync(
            lambda: cache.get_or_compute(key, _compute, cooldown)
        )
        cache.maybe_refresh(key, _compute, cooldown)
        logger.info("similar-jobs: cache=%s results=%d", "hit" if was_hit else "miss", len(recommendations))
    else:
        recommendations = await anyio.to_thread.run_sync(_compute)

    processing_time = f"{int((time.time() - start_time) * 1000)}ms"
    logger.info("✓ Found %d similar jobs in %s", len(recommendations), processing_time)

    return RecommendationResponse(
        success=True,
        data=RecommendationData(
            totalResults=len(recommendations),
            recommendations=recommendations,
            processingTime=processing_time
        ),
        message=f"Found {len(recommendations)} similar jobs"
    )


# ---------------------------------------------------------------------------
# search
# ---------------------------------------------------------------------------

@router.post("/search", response_model=RecommendationResponse)
async def semantic_search(request: SemanticSearchRequest, req: Request):
    """
    Semantic search for jobs using text query.

    - **query**: Search query text
    - **topK**: Number of results
    - **filters**: Optional filters
    """
    from app.config import get_settings
    from app.services.recommendation_cache import RecommendationCache, get_recommendation_cache

    start_time = time.time()
    settings = get_settings()

    faiss_manager = req.app.state.faiss_manager()
    embedding_generator = req.app.state.embedding_generator()

    if not faiss_manager or not embedding_generator:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Service not ready")

    logger.info("Semantic search: '%.50s...' topK=%d", request.query, request.topK)

    filters = _prepare_filters(request.filters)

    def _compute() -> List[JobRecommendation]:
        query_embedding = embedding_generator.encode_resume(request.query)
        results = faiss_manager.search(query_embedding=query_embedding, top_k=request.topK, filters=filters)
        return [_create_job_recommendation(r, i + 1) for i, r in enumerate(results)]

    if settings.RECOMMENDATION_CACHE_ENABLED:
        cache = get_recommendation_cache()
        key = RecommendationCache.key_for_search(request.query, filters, request.topK)
        cooldown = float(settings.RECOMMENDATION_CACHE_COOLDOWN_SECONDS)
        recommendations, was_hit = await anyio.to_thread.run_sync(
            lambda: cache.get_or_compute(key, _compute, cooldown)
        )
        cache.maybe_refresh(key, _compute, cooldown)
        logger.info("search: cache=%s results=%d", "hit" if was_hit else "miss", len(recommendations))
    else:
        recommendations = await anyio.to_thread.run_sync(_compute)

    processing_time = f"{int((time.time() - start_time) * 1000)}ms"
    logger.info("✓ Found %d matching jobs in %s", len(recommendations), processing_time)

    return RecommendationResponse(
        success=True,
        data=RecommendationData(
            totalResults=len(recommendations),
            recommendations=recommendations,
            processingTime=processing_time
        ),
        message=f"Found {len(recommendations)} matching jobs"
    )


# ---------------------------------------------------------------------------
# Admin
# ---------------------------------------------------------------------------

@router.post("/admin/sync", tags=["Admin"])
async def trigger_sync(req: Request):
    """Trigger manual sync from Job Service (Admin endpoint)"""
    start_time = time.time()

    faiss_manager = req.app.state.faiss_manager()
    embedding_generator = req.app.state.embedding_generator()

    if not faiss_manager or not embedding_generator:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Service not ready")

    logger.info("Manual sync triggered by admin")

    from app.services.job_sync import sync_jobs_from_service
    synced_count = await sync_jobs_from_service(faiss_manager, embedding_generator)

    processing_time = f"{int((time.time() - start_time) * 1000)}ms"
    return {
        "success": True,
        "message": f"Successfully synced {synced_count} jobs",
        "jobsSynced": synced_count,
        "processingTime": processing_time
    }
