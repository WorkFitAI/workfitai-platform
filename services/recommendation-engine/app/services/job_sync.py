"""
Initial sync - fetch jobs from Job Service and build FAISS index
"""

import logging
import httpx
from typing import List, Dict

from app.config import get_settings
from app.services import job_formatter

logger = logging.getLogger(__name__)


def _normalize_job_data(job: Dict) -> Dict:
    """
    Normalize job data from Job Service API to internal format
    
    Maps API fields to expected format:
    - postId -> id
    - shortDescription -> description
    - skillNames -> skills
    - company.name -> company.companyName
    """
    return {
        "id": job.get("postId"),
        "title": job.get("title"),
        "description": job.get("shortDescription"),
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


async def sync_jobs_from_service(faiss_manager, embedding_generator) -> int:
    """
    Fetch all active jobs from Job Service and build FAISS index
    
    Args:
        faiss_manager: FAISS index manager
        embedding_generator: Embedding service
    
    Returns:
        Number of jobs synced
    """
    settings = get_settings()
    
    try:
        logger.info("Starting initial job sync from Job Service...")
        logger.info(f"Job Service URL: {settings.JOB_SERVICE_URL}")
        
        synced_count = 0
        page = 0
        page_size = 50
        
        async with httpx.AsyncClient(timeout=settings.JOB_SERVICE_TIMEOUT) as client:
            while True:
                try:
                    # Fetch paginated jobs via API Gateway
                    url = f"{settings.JOB_SERVICE_URL}/job/public/jobs"
                    params = {"page": page, "size": page_size}
                    
                    logger.info(f"Fetching page {page} (size={page_size})...")
                    response = await client.get(url, params=params)
                    response.raise_for_status()
                    
                    data = response.json()
                    
                    # Extract jobs from response structure: data.result
                    jobs = data.get("data", {}).get("result", [])
                    meta = data.get("data", {}).get("meta", {})
                    
                    if not jobs:
                        logger.info(f"No more jobs found on page {page}")
                        break
                    
                    logger.info(f"Processing {len(jobs)} jobs from page {page}...")
                    
                    # Process each job
                    for job in jobs:
                        try:
                            # Job ID is 'postId' in this API
                            job_id = job.get("postId")
                            if not job_id:
                                logger.warning(f"Job missing postId, skipping: {job}")
                                continue
                            
                            # Normalize job data to internal format
                            normalized_job = _normalize_job_data(job)
                            
                            # Format job to text
                            job_text = job_formatter.format_job_as_text(normalized_job)
                            
                            # Generate embedding
                            embedding = embedding_generator.encode_job(job_text)
                            
                            # Add to FAISS (store normalized data)
                            faiss_manager.add_job_with_embedding(job_id, embedding, normalized_job)
                            synced_count += 1
                            
                        except Exception as e:
                            logger.error(f"Error processing job {job.get('id')}: {e}")
                            continue
                    
                    logger.info(f"✓ Synced {len(jobs)} jobs from page {page} (total: {synced_count})")
                    
                    # Check if last page using meta.page and meta.pages
                    current_page = meta.get("page", page + 1)
                    total_pages = meta.get("pages", 1)
                    
                    if current_page >= total_pages:
                        logger.info(f"Reached last page: {current_page}/{total_pages}")
                        break
                    
                    page += 1
                    
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
        
    except Exception as e:
        logger.error(f"Fatal error during job sync: {e}", exc_info=True)
        raise
