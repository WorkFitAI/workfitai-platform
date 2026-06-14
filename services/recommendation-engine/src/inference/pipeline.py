"""
CV Ranking Pipeline — three-stage inference.

Stage 1: Bi-Encoder + cosine similarity → top-K candidates
Stage 2: Cross-Encoder reranking → top-N (optional; degrades to bi-encoder)
Stage 3: T5 explanation generation → human-readable rationale (optional; degrades to rule-based)

Place trained weights under models/cv-refer/:
  bi-encoder/       — SentenceTransformer bi-encoder
  cross-encoder/    — CrossEncoder (sentence-transformers)
  explanation-t5/   — T5 seq2seq model (AutoModelForSeq2SeqLM)
"""

from __future__ import annotations

import os
import time
import logging
import yaml
from dataclasses import dataclass, field
from typing import List, Optional, Dict, Any

import numpy as np

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
    """

    def __init__(self, config: Dict[str, Any]):
        self.config = config
        self.model_dir = config.get("model_dir", "/app/models/cv-refer")
        self.top_k_default: int = config.get("top_k", 100)
        self.top_n_default: int = config.get("top_n", 20)
        self.min_score_default: float = config.get("min_score", 20.0)
        self.good_fit_threshold_default: float = config.get("good_fit_threshold", 55.0)

        self._bi_encoder = None
        self._cross_encoder = None
        self._t5_model = None
        self._t5_tokenizer = None
        self._batch_size: int = config.get("batch_size", 32)
        self._ready = False

        self._load_models()

    # ------------------------------------------------------------------
    # Model loading
    # ------------------------------------------------------------------

    def _load_models(self):
        """Load bi-encoder (required), cross-encoder and T5 (optional)."""
        from sentence_transformers import SentenceTransformer, CrossEncoder

        bi_encoder_path = os.path.join(
            self.model_dir, self.config.get("bi_encoder_model", "bi-encoder")
        )
        cross_encoder_path = os.path.join(
            self.model_dir, self.config.get("cross_encoder_model", "cross-encoder")
        )
        t5_path = os.path.join(
            self.model_dir, self.config.get("t5_model", "explanation-t5")
        )

        # Stage 1 model — required; failure prevents startup
        try:
            self._bi_encoder = SentenceTransformer(bi_encoder_path)
            logger.info("CVRankingPipeline: bi-encoder loaded from %s", bi_encoder_path)
        except Exception as e:
            logger.error(
                "CVRankingPipeline: bi-encoder failed to load from %s: %s — "
                "endpoint will return 503 until models are in place.",
                bi_encoder_path, e,
            )
            return  # _ready stays False

        # Stage 2 model — optional
        try:
            self._cross_encoder = CrossEncoder(cross_encoder_path)
            logger.info("CVRankingPipeline: cross-encoder loaded from %s", cross_encoder_path)
        except Exception as e:
            logger.warning(
                "CVRankingPipeline: cross-encoder not loaded (%s) — "
                "stage 2 will use bi-encoder scores only.", e
            )

        # Stage 3 model — optional
        try:
            from transformers import AutoTokenizer, AutoModelForSeq2SeqLM
            self._t5_tokenizer = AutoTokenizer.from_pretrained(t5_path)
            self._t5_model = AutoModelForSeq2SeqLM.from_pretrained(t5_path)
            self._t5_model.eval()
            logger.info("CVRankingPipeline: T5 explanation model loaded from %s", t5_path)
        except Exception as e:
            logger.warning(
                "CVRankingPipeline: T5 model not loaded (%s) — "
                "explanations will be rule-based.", e
            )

        self._ready = True
        logger.info(
            "CVRankingPipeline ready | bi-encoder: yes | cross-encoder: %s | T5: %s",
            "yes" if self._cross_encoder else "no (skipped)",
            "yes" if self._t5_model else "no (rule-based)",
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
            good_fit_threshold if good_fit_threshold is not None
            else self.good_fit_threshold_default
        )

        t0 = time.perf_counter()
        stage1 = self._stage1_retrieve(job, resumes, top_k)
        stage2 = self._stage2_rerank(job, stage1, top_n)
        ranked_resumes = self._stage3_explain(stage2, good_fit_threshold, min_score)
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
    # Stage implementations
    # ------------------------------------------------------------------

    def _stage1_retrieve(
        self, job: JobInput, resumes: List[ResumeInput], top_k: int
    ) -> List[Dict[str, Any]]:
        """Bi-encoder: encode job + resumes, return top_k by cosine similarity (0-100)."""
        job_text = _build_job_text(job)
        resume_texts = [_build_resume_text(r) for r in resumes]

        # E5 convention: job is the query, CVs are passages
        job_emb = self._bi_encoder.encode(
            f"query: {job_text}",
            normalize_embeddings=True,
            convert_to_numpy=True,
            show_progress_bar=False,
        )
        resume_embs = self._bi_encoder.encode(
            [f"passage: {t}" for t in resume_texts],
            normalize_embeddings=True,
            convert_to_numpy=True,
            batch_size=self._batch_size,
            show_progress_bar=False,
        )

        # Dot product of L2-normalised vectors = cosine similarity ∈ [-1, 1]
        scores = resume_embs @ job_emb
        scores_100 = ((np.clip(scores, -1.0, 1.0) + 1.0) / 2.0) * 100.0

        top_indices = np.argsort(scores_100)[::-1][:top_k]

        return [
            {
                "resume_index": resumes[i].resume_index,
                "similarity_score": round(float(scores_100[i]), 2),
                "score": round(float(scores_100[i]), 2),
                "_resume": resumes[i],
                "_job_text": job_text,
            }
            for i in top_indices
        ]

    def _stage2_rerank(
        self, job: JobInput, candidates: List[Dict[str, Any]], top_n: int
    ) -> List[Dict[str, Any]]:
        """Cross-encoder: rerank top-K candidates to top-N. Falls back to bi-encoder order."""
        if not candidates:
            return []

        if self._cross_encoder is None:
            for c in candidates:
                c["cross_score"] = c["similarity_score"]
            return candidates[:top_n]

        job_text = candidates[0].get("_job_text", _build_job_text(job))
        pairs = [[job_text, _build_resume_text(c["_resume"])] for c in candidates]

        try:
            raw_scores = self._cross_encoder.predict(
                pairs, convert_to_numpy=True, show_progress_bar=False
            )
            # Sigmoid → [0, 1] → scale to [0, 100]
            scores_100 = (1.0 / (1.0 + np.exp(-raw_scores))) * 100.0

            for c, ce_score in zip(candidates, scores_100):
                c["cross_score"] = round(float(ce_score), 2)
                # Blended final score: 40% bi-encoder + 60% cross-encoder
                c["score"] = round(0.4 * c["similarity_score"] + 0.6 * float(ce_score), 2)

            candidates.sort(key=lambda c: c["score"], reverse=True)
        except Exception as e:
            logger.warning("Stage 2 cross-encoder failed (%s); using bi-encoder scores.", e)
            for c in candidates:
                c["cross_score"] = c["similarity_score"]

        return candidates[:top_n]

    def _stage3_explain(
        self,
        ranked: List[Dict[str, Any]],
        good_fit_threshold: float,
        min_score: float,
    ) -> List[RankedResume]:
        """Generate explanations, apply label, filter by min_score."""
        results = []
        for c in ranked:
            score = c["score"]
            if score < min_score:
                continue
            label = "Good Fit" if score >= good_fit_threshold else "No Fit"
            explanation = self._explain(c, label)
            results.append(RankedResume(
                resume_index=c["resume_index"],
                score=round(score, 2),
                similarity_score=c["similarity_score"],
                cross_score=c.get("cross_score", c["similarity_score"]),
                label=label,
                explanation=explanation,
            ))
        return results

    def _explain(self, candidate: Dict[str, Any], label: str) -> str:
        """T5 explanation with rule-based fallback."""
        if self._t5_model is not None:
            try:
                return _t5_generate(
                    self._t5_model,
                    self._t5_tokenizer,
                    candidate.get("_job_text", ""),
                    _build_resume_text(candidate["_resume"]),
                )
            except Exception as e:
                logger.warning("T5 generation failed (%s); using rule-based fallback.", e)
        return _rule_explanation(candidate["score"])


# ---------------------------------------------------------------------------
# Pure helper functions (no model state)
# ---------------------------------------------------------------------------

def _build_job_text(job: JobInput) -> str:
    parts = []
    if job.jd_overview:
        parts.append(f"Overview: {job.jd_overview}")
    if job.jd_requirements:
        parts.append(f"Requirements: {job.jd_requirements}")
    if job.jd_responsibilities:
        parts.append(f"Responsibilities: {job.jd_responsibilities}")
    if job.jd_preferred:
        parts.append(f"Preferred: {job.jd_preferred}")
    return "\n".join(parts) or job.job_description_text


def _build_resume_text(resume: ResumeInput) -> str:
    parts = []
    if resume.resume_summary:
        parts.append(f"Summary: {resume.resume_summary}")
    if resume.resume_experience:
        parts.append(f"Experience: {resume.resume_experience}")
    if resume.resume_skills:
        parts.append(f"Skills: {resume.resume_skills}")
    if resume.resume_education:
        parts.append(f"Education: {resume.resume_education}")
    return "\n".join(parts) or resume.resume_text


def _t5_generate(model, tokenizer, job_text: str, resume_text: str) -> str:
    import torch
    prompt = (
        f"explain cv fit: job: {job_text[:200]} "
        f"| resume: {resume_text[:300]}"
    )
    inputs = tokenizer(prompt, return_tensors="pt", max_length=512, truncation=True)
    with torch.no_grad():
        outputs = model.generate(**inputs, max_new_tokens=80, num_beams=2, early_stopping=True)
    return tokenizer.decode(outputs[0], skip_special_tokens=True)


def _rule_explanation(score: float) -> str:
    if score >= 75:
        return "Strong match across skills, experience, and qualifications for this role."
    if score >= 55:
        return "Good alignment with key requirements; meets most criteria for this position."
    if score >= 35:
        return "Partial match; has some relevant skills but may lack key qualifications."
    return "Limited alignment with the job requirements based on profile analysis."
