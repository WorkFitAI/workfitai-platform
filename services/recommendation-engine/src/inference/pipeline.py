"""
CV Ranking Pipeline Interface
Stage 1: Bi-Encoder + FAISS  → top-K candidates
Stage 2: Cross-Encoder       → rerank to top-N
Stage 3: T5 Explanation      → natural language rationale

Place trained model weights under models/cv-refer/ and implement
the body of CVRankingPipeline to activate this pipeline.
"""

from __future__ import annotations

import os
import time
import yaml
import logging
from dataclasses import dataclass, field
from typing import List, Optional, Dict, Any

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Data classes (match api_contract.md exactly)
# ---------------------------------------------------------------------------

@dataclass
class JobInput:
    job_index: int
    jd_overview: str
    jd_requirements: str
    jd_responsibilities: str
    jd_preferred: str = ""
    job_description_text: str = ""  # display only — never fed to model


@dataclass
class ResumeInput:
    resume_index: int
    resume_summary: str
    resume_experience: str
    resume_skills: str
    resume_education: str = ""
    resume_text: str = ""  # display only — never fed to model


@dataclass
class RankedResume:
    resume_index: int
    score: float          # 0-100, rounded 2dp
    similarity_score: float
    cross_score: float
    label: str            # "Good Fit" | "No Fit"
    explanation: str


@dataclass
class RankResult:
    job_index: int
    job_overview: str
    total_candidates: int
    processing_time_ms: float
    ranked_resumes: List[RankedResume]


# ---------------------------------------------------------------------------
# Config loader
# ---------------------------------------------------------------------------

_DEFAULT_CONFIG_PATH = os.path.join(
    os.path.dirname(__file__), "..", "..", "config", "cv_ranking_config.yaml"
)


def load_config(config_path: Optional[str] = None) -> Dict[str, Any]:
    path = config_path or os.environ.get("CV_RANKING_CONFIG", _DEFAULT_CONFIG_PATH)
    with open(path, "r", encoding="utf-8") as f:
        return yaml.safe_load(f)


# ---------------------------------------------------------------------------
# Pipeline
# ---------------------------------------------------------------------------

class CVRankingPipeline:
    """
    Three-stage CV ranking pipeline.

    Initialization loads all three models once; rank() is called per request.
    Thread-safe for concurrent read-only inference (eval mode, no state mutation).

    To activate: place model weights in models/cv-refer/ and implement
    _stage1_retrieve, _stage2_rerank, _stage3_explain below.
    """

    def __init__(self, config: Dict[str, Any]):
        self.config = config
        self.model_dir = config.get("model_dir", "/app/models/cv-refer")

        self.top_k_default: int = config.get("top_k", 100)
        self.top_n_default: int = config.get("top_n", 20)
        self.min_score_default: float = config.get("min_score", 20.0)
        self.good_fit_threshold_default: float = config.get("good_fit_threshold", 55.0)

        self._load_models()

    def _load_models(self):
        """Load bi-encoder, cross-encoder, and T5 explanation models."""
        # TODO: implement when model weights are placed in self.model_dir
        # Example structure expected under self.model_dir:
        #   bi-encoder/       → sentence-transformers model
        #   cross-encoder/    → CrossEncoder model (MiniLM + MarginMSELoss)
        #   t5-explanation/   → T5 fine-tuned for explanation generation
        #
        # raise NotImplementedError if you want hard failure;
        # set self._ready = False for graceful degradation.
        self._ready = False
        logger.warning(
            "CVRankingPipeline: model weights not found. "
            "Place models under %s and implement _load_models().",
            self.model_dir,
        )

    @property
    def is_ready(self) -> bool:
        return self._ready

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    def rank(
        self,
        job: JobInput,
        resumes: List[ResumeInput],
        top_k: Optional[int] = None,
        top_n: Optional[int] = None,
        min_score: Optional[float] = None,
        good_fit_threshold: Optional[float] = None,
    ) -> RankResult:
        if not self._ready:
            raise RuntimeError(
                "CVRankingPipeline is not ready. "
                "Ensure model weights are placed in the cv-refer models directory."
            )

        top_k = min(top_k or self.top_k_default, len(resumes))
        top_n = min(top_n or self.top_n_default, top_k)
        min_score = min_score if min_score is not None else self.min_score_default
        good_fit_threshold = (
            good_fit_threshold
            if good_fit_threshold is not None
            else self.good_fit_threshold_default
        )

        t0 = time.perf_counter()

        # Stage 1: bi-encoder retrieval
        stage1_candidates = self._stage1_retrieve(job, resumes, top_k)

        # Stage 2: cross-encoder reranking
        stage2_ranked = self._stage2_rerank(job, stage1_candidates, top_n)

        # Stage 3: natural language explanation
        ranked_resumes = self._stage3_explain(stage2_ranked, good_fit_threshold, min_score)

        processing_ms = (time.perf_counter() - t0) * 1000

        return RankResult(
            job_index=job.job_index,
            job_overview=job.jd_overview[:200],
            total_candidates=len(resumes),
            processing_time_ms=round(processing_ms, 1),
            ranked_resumes=ranked_resumes,
        )

    def to_dict(self, result: RankResult) -> Dict[str, Any]:
        return {
            "job_index": result.job_index,
            "job_overview": result.job_overview,
            "total_candidates": result.total_candidates,
            "processing_time_ms": result.processing_time_ms,
            "ranked_resumes": [
                {
                    "resume_index": r.resume_index,
                    "score": r.score,
                    "similarity_score": r.similarity_score,
                    "cross_score": r.cross_score,
                    "label": r.label,
                    "explanation": r.explanation,
                }
                for r in result.ranked_resumes
            ],
        }

    # ------------------------------------------------------------------
    # Stage implementations — fill these in with real model calls
    # ------------------------------------------------------------------

    def _stage1_retrieve(
        self, job: JobInput, resumes: List[ResumeInput], top_k: int
    ) -> List[Dict[str, Any]]:
        """Bi-encoder + FAISS: return top_k candidates with similarity_score (0-100)."""
        raise NotImplementedError("Implement Stage 1: bi-encoder + FAISS retrieval")

    def _stage2_rerank(
        self, job: JobInput, candidates: List[Dict[str, Any]], top_n: int
    ) -> List[Dict[str, Any]]:
        """Cross-encoder: rerank candidates, add cross_score (0-100) and final score."""
        raise NotImplementedError("Implement Stage 2: cross-encoder reranking")

    def _stage3_explain(
        self,
        ranked: List[Dict[str, Any]],
        good_fit_threshold: float,
        min_score: float,
    ) -> List[RankedResume]:
        """T5: generate explanation; apply label and min_score filter."""
        raise NotImplementedError("Implement Stage 3: T5 explanation generation")
