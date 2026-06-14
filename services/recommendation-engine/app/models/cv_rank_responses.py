"""
Pydantic response models for CV ranking endpoint (api_contract.md)
"""

from typing import List
from pydantic import BaseModel, Field


class RankedResumeResponse(BaseModel):
    resume_index: int = Field(..., description="Echoed from request for mapping")
    score: float = Field(..., description="Final composite score: 0.3*similarity + 0.7*cross (0-100)")
    similarity_score: float = Field(..., description="Bi-encoder cosine similarity score (0-100)")
    cross_score: float = Field(..., description="Cross-encoder contextual score (0-100)")
    label: str = Field(..., description='"Good Fit" or "No Fit"')
    explanation: str = Field(..., description="T5-generated natural language rationale")


class CvRankResponse(BaseModel):
    job_index: int = Field(..., description="Echoed from request")
    job_overview: str = Field(..., description="First 200 chars of jd_overview (for display)")
    total_candidates: int = Field(..., description="Total CVs received in the request")
    processing_time_ms: float = Field(..., description="End-to-end pipeline latency in milliseconds")
    ranked_resumes: List[RankedResumeResponse] = Field(
        ..., description="CVs with score >= min_score, sorted by score descending"
    )


class RankedApplicantResponse(BaseModel):
    """Extended ranked resume that includes the applicant's username — used by rank-by-job."""
    username: str = Field(..., description="Applicant's username (JWT sub from application)")
    score: float = Field(..., description="Final composite score (0-100)")
    similarity_score: float = Field(..., description="Bi-encoder cosine similarity score (0-100)")
    cross_score: float = Field(..., description="Cross-encoder contextual score (0-100)")
    label: str = Field(..., description='"Good Fit" or "No Fit"')
    explanation: str = Field(..., description="T5-generated natural language rationale")


class CvRankByJobResponse(BaseModel):
    """Response for rank-by-job/{jobId} — includes username per ranked applicant."""
    job_id: str = Field(..., description="Job ID from path param")
    job_overview: str = Field(..., description="First 200 chars of jd_overview (for display)")
    total_candidates: int = Field(..., description="Total applicants in ranking pool")
    ranked_count: int = Field(..., description="Applicants with score >= min_score (returned)")
    processing_time_ms: float = Field(..., description="End-to-end pipeline latency in milliseconds")
    ranked_applicants: List[RankedApplicantResponse] = Field(
        ..., description="Ranked applicants with score >= min_score, sorted by score descending"
    )
