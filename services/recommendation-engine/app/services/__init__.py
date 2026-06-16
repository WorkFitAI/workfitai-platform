"""
Core services for recommendation engine.

Heavy ML dependencies (EmbeddingGenerator, FAISSIndexManager, ResumeParser)
are imported lazily inside lifespan/routes to avoid loading torch at import time,
which would break lightweight imports (e.g. cache modules, tests).
"""

from .job_formatter import format_job_as_text  # lightweight, safe to eager-import

__all__ = ['format_job_as_text']
