"""
Pydantic request models for CV ranking endpoint (api_contract.md)
"""

from typing import Optional, List
from pydantic import BaseModel, Field


class JobRequest(BaseModel):
    job_index: int = Field(default=-1, description="Job ID in your system; -1 if unavailable")
    jd_overview: str = Field(..., description="Role overview (max 512 chars used by model)")
    jd_requirements: str = Field(..., description="Mandatory requirements (max 512 chars used)")
    jd_responsibilities: str = Field(..., description="Specific duties (max 400 chars used)")
    jd_preferred: str = Field(default="", description="Nice-to-have requirements (max 256 chars used)")
    job_description_text: str = Field(default="", description="Full raw JD — display only, never fed to model")


class ResumeRequest(BaseModel):
    resume_index: int = Field(..., description="Unique CV ID within this request (echoed back for mapping)")
    resume_summary: str = Field(..., description="Candidate profile summary (max 400 chars used)")
    resume_experience: str = Field(..., description="Work history (max 512 chars used)")
    resume_skills: str = Field(..., description="Technical and soft skills (max 300 chars used)")
    resume_education: str = Field(default="", description="Education background (max 256 chars used)")
    resume_text: str = Field(default="", description="Full raw CV — display only, never fed to model")


class OptionsRequest(BaseModel):
    top_k: Optional[int] = Field(default=None, description="Stage-1 bi-encoder candidate pool size (default 100)")
    top_n: Optional[int] = Field(default=None, description="Final results returned after cross-encoder (default 20)")
    min_score: Optional[float] = Field(default=None, description="Minimum score to appear in results (default 20)")
    good_fit_threshold: Optional[float] = Field(default=None, description="Score threshold for 'Good Fit' label (default 55)")


class CvRankRequest(BaseModel):
    job: JobRequest
    resumes: List[ResumeRequest] = Field(..., min_length=1, description="List of CVs to rank")
    options: OptionsRequest = Field(default_factory=OptionsRequest)
