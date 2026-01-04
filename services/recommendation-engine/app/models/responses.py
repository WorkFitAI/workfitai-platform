"""
Pydantic response models for API endpoints
"""

from typing import List, Optional, Dict, Any
from pydantic import BaseModel, Field
from datetime import datetime


class JobRecommendation(BaseModel):
    """Single job recommendation with score and metadata"""
    jobId: str = Field(..., description="Job ID")
    score: float = Field(..., description="Final similarity score (0-1)", ge=0, le=1.01)  # Allow slight float precision overflow
    rank: int = Field(default=1, description="Ranking position (1-based)", ge=1)
    
    # Scoring details (for 2-stage pipeline)
    biEncoderScore: Optional[float] = Field(default=None, description="Bi-encoder (FAISS) score")
    crossEncoderScore: Optional[float] = Field(default=None, description="Cross-encoder reranking score")
    
    # Job details (optional, from metadata)
    title: Optional[str] = Field(default=None, description="Job title")
    company: Optional[str] = Field(default=None, description="Company name")
    location: Optional[str] = Field(default=None, description="Job location")
    salary: Optional[str] = Field(default=None, description="Salary range")
    experienceLevel: Optional[str] = Field(default=None, description="Experience level required")
    jobType: Optional[str] = Field(default=None, description="Job type (Full-time, Part-time, etc.)")
    skills: Optional[List[str]] = Field(default=None, description="Required skills")
    
    # Matching metadata
    matchedSkills: Optional[List[str]] = Field(default=None, description="Skills that matched")
    matchReasons: Optional[List[str]] = Field(default=None, description="Reasons for the match")


class ResumeAnalysis(BaseModel):
    """Analysis of resume content"""
    extractedSkills: List[str] = Field(default_factory=list, description="Skills extracted from resume")
    experienceYears: Optional[int] = Field(default=None, description="Years of experience detected")
    educationLevel: Optional[str] = Field(default=None, description="Education level detected")
    contactInfo: Optional[Dict[str, Any]] = Field(default=None, description="Contact information")


class RecommendationData(BaseModel):
    """Recommendation results data"""
    totalResults: int = Field(..., description="Total number of results returned")
    recommendations: List[JobRecommendation] = Field(..., description="List of job recommendations")
    processingTime: str = Field(..., description="Processing time (e.g., '120ms')")
    resumeAnalysis: Optional[ResumeAnalysis] = Field(default=None, description="Resume analysis (if applicable)")


class RecommendationResponse(BaseModel):
    """Standard recommendation response"""
    success: bool = Field(default=True, description="Operation success status")
    data: RecommendationData = Field(..., description="Recommendation data")
    message: Optional[str] = Field(default=None, description="Optional message")


class ModelStatus(BaseModel):
    """Model loading status"""
    loaded: bool = Field(..., description="Whether model is loaded")
    name: Optional[str] = Field(default=None, description="Model name")
    size: Optional[str] = Field(default=None, description="Model size")
    path: Optional[str] = Field(default=None, description="Model path")


class FAISSIndexStatus(BaseModel):
    """FAISS index status"""
    loaded: bool = Field(..., description="Whether index is loaded")
    totalJobs: int = Field(..., description="Total jobs indexed")
    dimension: int = Field(..., description="Vector dimension")
    lastUpdated: Optional[datetime] = Field(default=None, description="Last update timestamp")


class KafkaConsumerStatus(BaseModel):
    """Kafka consumer status"""
    connected: bool = Field(..., description="Connection status")
    consumerGroup: str = Field(..., description="Consumer group ID")
    subscribedTopics: List[str] = Field(..., description="Subscribed topics")


class HealthCheckData(BaseModel):
    """Health check response data"""
    status: str = Field(..., description="Overall health status")
    model: ModelStatus = Field(..., description="Model status")
    faissIndex: FAISSIndexStatus = Field(..., description="FAISS index status")
    kafkaConsumer: KafkaConsumerStatus = Field(..., description="Kafka consumer status")


class HealthCheckResponse(BaseModel):
    """Health check response"""
    success: bool = Field(default=True)
    data: HealthCheckData


class RebuildIndexResponse(BaseModel):
    """Response for index rebuild request"""
    success: bool = Field(..., description="Operation success status")
    message: str = Field(..., description="Status message")
    jobsProcessed: Optional[int] = Field(default=None, description="Number of jobs processed")
    processingTime: Optional[str] = Field(default=None, description="Processing time")


class ErrorResponse(BaseModel):
    """Error response"""
    success: bool = Field(default=False)
    error: str = Field(..., description="Error type")
    message: str = Field(..., description="Error message")
    details: Optional[Any] = Field(default=None, description="Additional error details")
