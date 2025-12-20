"""
API route handlers for recommendation endpoints
"""

import base64
import time
import logging
from typing import List, Dict
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
    
    # Location filter (use first location if multiple)
    if request_filters.locations and len(request_filters.locations) > 0:
        filters['location'] = request_filters.locations[0]
    
    # Salary filters
    if request_filters.minSalary:
        filters['minSalary'] = request_filters.minSalary
    if request_filters.maxSalary:
        filters['maxSalary'] = request_filters.maxSalary
    
    # Experience level (use first if multiple)
    if request_filters.experienceLevels and len(request_filters.experienceLevels) > 0:
        filters['experienceLevel'] = request_filters.experienceLevels[0]
    
    # Employment type (use first if multiple)
    if request_filters.employmentTypes and len(request_filters.employmentTypes) > 0:
        filters['jobType'] = request_filters.employmentTypes[0]
    
    return filters


def _create_job_recommendation(result: Dict, rank: int) -> JobRecommendation:
    """Create JobRecommendation from FAISS search result"""
    # Clamp score to [0, 1] range to handle floating point precision
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


@router.post("/by-resume", response_model=RecommendationResponse)
async def recommend_by_resume(request: RecommendByResumeRequest, req: Request):
    """
    Get job recommendations based on resume PDF
    
    - **resumeFile**: Base64 encoded PDF file
    - **topK**: Number of recommendations (1-100)
    - **filters**: Optional filters for location, salary, etc.
    """
    start_time = time.time()
    
    try:
        # Get services from app state
        faiss_manager = req.app.state.faiss_manager()
        embedding_generator = req.app.state.embedding_generator()
        resume_parser = req.app.state.resume_parser()
        
        if not faiss_manager or not embedding_generator or not resume_parser:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Service not ready"
            )
        
        logger.info(f"Recommend by resume (base64 length: {len(request.resumeFile)}, topK={request.topK})")
        
        # Decode base64 PDF
        try:
            pdf_bytes = base64.b64decode(request.resumeFile)
        except Exception as e:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Invalid base64 encoding: {str(e)}"
            )
        
        # Parse resume
        try:
            parsed_data = resume_parser.parse_resume(pdf_bytes)
            if not parsed_data:
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail="Failed to parse resume"
                )
        except Exception as e:
            logger.error(f"Resume parsing error: {e}", exc_info=True)
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Resume parsing failed: {str(e)}"
            )
        
        # Format resume for embedding
        formatted_text = resume_parser.format_resume_for_matching(parsed_data)
        logger.info(f"Formatted resume text length: {len(formatted_text)}")
        
        # Generate embedding
        query_embedding = embedding_generator.encode_resume(formatted_text)
        
        # Prepare filters
        filters = _prepare_filters(request.filters)
        
        # Search FAISS
        results = faiss_manager.search(
            query_embedding=query_embedding,
            top_k=request.topK,
            filters=filters
        )
        
        # Convert to response format
        recommendations = [
            _create_job_recommendation(result, idx + 1)
            for idx, result in enumerate(results)
        ]
        
        processing_time = f"{int((time.time() - start_time) * 1000)}ms"
        
        logger.info(f"✓ Found {len(recommendations)} matching jobs in {processing_time}")
        
        return RecommendationResponse(
            success=True,
            data=RecommendationData(
                totalResults=len(recommendations),
                recommendations=recommendations,
                processingTime=processing_time
            ),
            message=f"Found {len(recommendations)} matching jobs"
        )
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error in recommend_by_resume: {e}", exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )


@router.post("/by-profile", response_model=RecommendationResponse)
async def recommend_by_profile(request: RecommendByProfileRequest, req: Request):
    """
    Get job recommendations based on text profile
    
    - **profileText**: User profile text (skills, experience)
    - **topK**: Number of recommendations
    - **filters**: Optional filters
    """
    start_time = time.time()
    
    try:
        faiss_manager = req.app.state.faiss_manager()
        embedding_generator = req.app.state.embedding_generator()
        
        if not faiss_manager or not embedding_generator:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Service not ready"
            )
        
        logger.info(f"Recommend by profile (text length: {len(request.profileText)}, topK={request.topK})")
        
        # Generate embedding from profile text (use encode_resume for query)
        query_embedding = embedding_generator.encode_resume(request.profileText)
        
        # Prepare filters
        filters = _prepare_filters(request.filters)
        
        # Search FAISS
        results = faiss_manager.search(
            query_embedding=query_embedding,
            top_k=request.topK,
            filters=filters
        )
        
        # Convert to response format
        recommendations = [
            _create_job_recommendation(result, idx + 1)
            for idx, result in enumerate(results)
        ]
        
        processing_time = f"{int((time.time() - start_time) * 1000)}ms"
        
        logger.info(f"✓ Found {len(recommendations)} matching jobs in {processing_time}")
        
        return RecommendationResponse(
            success=True,
            data=RecommendationData(
                totalResults=len(recommendations),
                recommendations=recommendations,
                processingTime=processing_time
            ),
            message=f"Found {len(recommendations)} matching jobs"
        )
        
    except Exception as e:
        logger.error(f"Error in recommend_by_profile: {e}", exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )


@router.post("/similar-jobs", response_model=RecommendationResponse)
async def find_similar_jobs(request: SimilarJobsRequest, req: Request):
    """
    Find jobs similar to a reference job
    
    - **jobId**: Reference job ID
    - **topK**: Number of similar jobs
    - **excludeSameCompany**: Exclude jobs from same company
    """
    start_time = time.time()
    
    try:
        faiss_manager = req.app.state.faiss_manager()
        
        if not faiss_manager:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Service not ready"
            )
        
        logger.info(f"Finding similar jobs for {request.jobId} (topK={request.topK}, excludeSameCompany={request.excludeSameCompany})")
        
        # Get the reference job's embedding from FAISS
        job_data = faiss_manager.get_job_by_id(request.jobId)
        if not job_data:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail=f"Job {request.jobId} not found in index"
            )
        
        query_embedding = job_data['embedding']
        reference_company = _extract_company_name(job_data)
        
        # Search for similar jobs (request topK + 1 to exclude self)
        results = faiss_manager.search(
            query_embedding=query_embedding,
            top_k=request.topK + 1,
            filters={}
        )
        
        # Filter results
        recommendations = []
        for result in results:
            # Skip the reference job itself
            if result['jobId'] == request.jobId:
                continue
            
            # Extract company for comparison
            result_company = _extract_company_name(result)
                
            # Skip same company if requested
            if request.excludeSameCompany and result_company == reference_company:
                continue
            
            recommendations.append(
                _create_job_recommendation(result, len(recommendations) + 1)
            )
            
            # Stop when we have enough results
            if len(recommendations) >= request.topK:
                break
        
        processing_time = f"{int((time.time() - start_time) * 1000)}ms"
        
        logger.info(f"✓ Found {len(recommendations)} similar jobs in {processing_time}")
        
        return RecommendationResponse(
            success=True,
            data=RecommendationData(
                totalResults=len(recommendations),
                recommendations=recommendations,
                processingTime=processing_time
            ),
            message=f"Found {len(recommendations)} similar jobs"
        )
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error in find_similar_jobs: {e}", exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )


@router.post("/search", response_model=RecommendationResponse)
async def semantic_search(request: SemanticSearchRequest, req: Request):
    """
    Semantic search for jobs using text query
    
    - **query**: Search query text
    - **topK**: Number of results
    - **filters**: Optional filters
    """
    start_time = time.time()
    
    try:
        faiss_manager = req.app.state.faiss_manager()
        embedding_generator = req.app.state.embedding_generator()
        
        if not faiss_manager or not embedding_generator:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Service not ready"
            )
        
        logger.info(f"Semantic search: '{request.query[:50]}...' topK={request.topK}")
        
        # Generate query embedding (use encode_resume for query prefix)
        query_embedding = embedding_generator.encode_resume(request.query)
        
        # Prepare filters
        filters = _prepare_filters(request.filters)
        
        # Search FAISS
        results = faiss_manager.search(
            query_embedding=query_embedding,
            top_k=request.topK,
            filters=filters
        )
        
        # Convert to response format
        recommendations = [
            _create_job_recommendation(result, idx + 1)
            for idx, result in enumerate(results)
        ]
        
        processing_time = f"{int((time.time() - start_time) * 1000)}ms"
        
        logger.info(f"✓ Found {len(recommendations)} matching jobs in {processing_time}")
        
        return RecommendationResponse(
            success=True,
            data=RecommendationData(
                totalResults=len(recommendations),
                recommendations=recommendations,
                processingTime=processing_time
            ),
            message=f"Found {len(recommendations)} matching jobs"
        )
        
    except Exception as e:
        logger.error(f"Error in semantic_search: {e}", exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )


@router.post("/admin/sync", tags=["Admin"])
async def trigger_sync(req: Request):
    """
    Trigger manual sync from Job Service (Admin endpoint)
    """
    start_time = time.time()
    
    try:
        faiss_manager = req.app.state.faiss_manager()
        embedding_generator = req.app.state.embedding_generator()
        
        if not faiss_manager or not embedding_generator:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Service not ready"
            )
        
        logger.info("Manual sync triggered by admin")
        
        # Import here to avoid circular dependency
        from app.services.job_sync import sync_jobs_from_service
        
        synced_count = await sync_jobs_from_service(faiss_manager, embedding_generator)
        
        processing_time = f"{int((time.time() - start_time) * 1000)}ms"
        
        return {
            "success": True,
            "message": f"Successfully synced {synced_count} jobs",
            "jobsSynced": synced_count,
            "processingTime": processing_time
        }
        
    except Exception as e:
        logger.error(f"Error in manual sync: {e}", exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )
