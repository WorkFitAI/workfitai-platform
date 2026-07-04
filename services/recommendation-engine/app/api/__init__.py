"""
API routes and endpoints
"""

from .job_recommend_routes import router
from .cv_rank_routes import router as cv_rank_router

__all__ = ['router', 'cv_rank_router']
