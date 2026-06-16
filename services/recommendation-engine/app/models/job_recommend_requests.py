"""
Pydantic request models for API endpoints
"""

from typing import Optional, List
from pydantic import BaseModel, Field, field_validator


class RecommendationFilters(BaseModel):
    """Filters for job recommendations"""
    locations: Optional[List[str]] = Field(default=None, description="Filter by locations")
    employmentTypes: Optional[List[str]] = Field(default=None, description="Filter by employment types (FULL_TIME, PART_TIME, CONTRACT, etc.)")
    experienceLevels: Optional[List[str]] = Field(default=None, description="Filter by experience levels (ENTRY, MID_LEVEL, SENIOR, etc.)")
    minSalary: Optional[float] = Field(default=None, description="Minimum salary", ge=0)
    maxSalary: Optional[float] = Field(default=None, description="Maximum salary", ge=0)
    skills: Optional[List[str]] = Field(default=None, description="Filter by required skills")


class RecommendByResumeRequest(BaseModel):
    """Request for job recommendations based on resume"""
    resumeFile: str = Field(..., description="Base64 encoded PDF resume file")
    topK: int = Field(default=20, description="Number of recommendations to return", ge=1, le=100)
    filters: Optional[RecommendationFilters] = Field(default=None, description="Optional filters")
    
    @field_validator('topK')
    @classmethod
    def validate_top_k(cls, v):
        if v < 1 or v > 100:
            raise ValueError('topK must be between 1 and 100')
        return v


class RecommendByProfileRequest(BaseModel):
    """Request for job recommendations based on text profile"""
    profileText: str = Field(..., description="User profile text (skills, experience, etc.)", min_length=10)
    skills: Optional[List[str]] = Field(default=None, description="List of skills")
    experienceYears: Optional[int] = Field(default=None, description="Years of experience", ge=0)
    preferredLocation: Optional[str] = Field(default=None, description="Preferred job location")
    topK: int = Field(default=20, description="Number of recommendations to return", ge=1, le=100)
    filters: Optional[RecommendationFilters] = Field(default=None, description="Optional filters")


class SimilarJobsRequest(BaseModel):
    """Request for finding similar jobs"""
    jobId: str = Field(..., description="Reference job ID to find similar jobs")
    topK: int = Field(default=10, description="Number of similar jobs to return", ge=1, le=100)
    excludeSameCompany: bool = Field(default=True, description="Exclude jobs from the same company")


class SemanticSearchRequest(BaseModel):
    """Request for semantic job search"""
    query: str = Field(..., description="Search query text", min_length=3)
    topK: int = Field(default=15, description="Number of results to return", ge=1, le=100)
    filters: Optional[RecommendationFilters] = Field(default=None, description="Optional filters")


class RebuildIndexRequest(BaseModel):
    """Request for rebuilding FAISS index (admin only)"""
    adminToken: str = Field(..., description="Admin authentication token")
    async_: bool = Field(default=True, alias="async", description="Run rebuild asynchronously")
