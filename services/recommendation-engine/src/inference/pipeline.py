"""
CV Ranking Pipeline — 3-stage inference
Stage 1: Bi-Encoder + FAISS  → top-K candidates (similarity_score)
Stage 2: Cross-Encoder       → rerank to top-N   (cross_score + final_score)
Stage 3: T5 / template       → natural language explanation + label

Input contract (api_contract.md):
  JobInput   : jd_overview/512, jd_requirements/512, jd_responsibilities/400, jd_preferred/256
  ResumeInput: resume_summary/400, resume_experience/512, resume_skills/300, resume_education/256
  Fields *_text (job_description_text, resume_text) are display-only — never fed to model.

Score fusion:
  final_score = 0.3 * similarity_score + 0.7 * cross_score   (all scores 0–100)

Labels (configurable via good_fit_threshold, default 55; calibrated optimum ~27):
  score >= good_fit_threshold → "Good Fit"
  score <  good_fit_threshold → "No Fit"
  score <  min_score          → excluded from results entirely
"""

from __future__ import annotations

import os
import time
import yaml
import logging
import numpy as np
import faiss
from dataclasses import dataclass, field
from typing import List, Optional, Dict, Any, Tuple

from sentence_transformers import SentenceTransformer, CrossEncoder

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
# Text assembly helpers
# ---------------------------------------------------------------------------

def _build_job_text(job: JobInput) -> str:
    """Assemble the 4 structured JD fields into a single model input string.
    Each field is hard-capped at its max length per the input contract."""
    parts = []
    if job.jd_overview:
        parts.append(job.jd_overview[:512])
    if job.jd_requirements:
        parts.append(job.jd_requirements[:512])
    if job.jd_responsibilities:
        parts.append(job.jd_responsibilities[:400])
    if job.jd_preferred:
        parts.append(job.jd_preferred[:256])
    return " ".join(parts).strip()


def _build_resume_text(resume: ResumeInput) -> str:
    """Assemble the 4 structured CV fields into a single model input string."""
    parts = []
    if resume.resume_summary:
        parts.append(resume.resume_summary[:400])
    if resume.resume_experience:
        parts.append(resume.resume_experience[:512])
    if resume.resume_skills:
        parts.append(resume.resume_skills[:300])
    if resume.resume_education:
        parts.append(resume.resume_education[:256])
    return " ".join(parts).strip()


# ---------------------------------------------------------------------------
# Explanation helper (template-based fallback when T5 is absent)
# ---------------------------------------------------------------------------

def _template_explanation(
    resume: ResumeInput,
    similarity_score: float,
    cross_score: float,
    final_score: float,
    label: str,
) -> str:
    skills_preview = resume.resume_skills[:80].rstrip(",").strip() if resume.resume_skills else ""
    if label == "Good Fit":
        if skills_preview:
            return (
                f"Candidate's profile aligns well with the job requirements "
                f"(score {final_score:.1f}). Key skills: {skills_preview}."
            )
        return f"Candidate's experience and background are a strong match for this role (score {final_score:.1f})."
    else:
        return (
            f"Candidate's profile partially matches the requirements "
            f"(score {final_score:.1f}) but does not meet the threshold for Good Fit."
        )


# ---------------------------------------------------------------------------
# Pipeline
# ---------------------------------------------------------------------------

class CVRankingPipeline:
    """
    Three-stage CV ranking pipeline.

    Initialization loads the bi-encoder and cross-encoder once at startup.
    T5 explanation model is optional; if absent, template-based explanations are used.

    Thread-safe for concurrent read-only inference (models in eval mode, no shared mutable state).
    """

    def __init__(self, config: Dict[str, Any]):
        self.config = config
        self.model_dir = config.get("model_dir", "/app/models/cv-refer")
        self.batch_size: int = config.get("batch_size", 32)
        self.device: str = config.get("device", "cpu")

        self.top_k_default: int = config.get("top_k", 100)
        self.top_n_default: int = config.get("top_n", 20)
        self.min_score_default: float = config.get("min_score", 20.0)
        self.good_fit_threshold_default: float = config.get("good_fit_threshold", 55.0)

        self._bi_encoder: Optional[SentenceTransformer] = None
        self._cross_encoder: Optional[CrossEncoder] = None
        self._t5_pipeline = None
        self._ready = False

        self._load_models()

    def _load_models(self):
        bi_encoder_name = self.config.get("bi_encoder_model", "bi-encoder")
        cross_encoder_name = self.config.get("cross_encoder_model", "cross-encoder")
        t5_name = self.config.get("t5_model", "t5-explanation")

        bi_encoder_path = os.path.join(self.model_dir, bi_encoder_name)
        cross_encoder_path = os.path.join(self.model_dir, cross_encoder_name)
        t5_path = os.path.join(self.model_dir, t5_name)

        # Bi-encoder (required)
        if not os.path.isdir(bi_encoder_path):
            logger.warning(
                "CVRankingPipeline: bi-encoder weights not found at %s. "
                "Pipeline will not be ready.",
                bi_encoder_path,
            )
            return

        try:
            logger.info("Loading bi-encoder from %s ...", bi_encoder_path)
            self._bi_encoder = SentenceTransformer(bi_encoder_path, device=self.device)
            self._bi_encoder.eval()
            logger.info("Bi-encoder loaded (dim=%d)", self._bi_encoder.get_sentence_embedding_dimension())
        except Exception:
            logger.exception("Failed to load bi-encoder")
            return

        # Cross-encoder (required)
        if not os.path.isdir(cross_encoder_path):
            logger.warning(
                "CVRankingPipeline: cross-encoder weights not found at %s. "
                "Pipeline will not be ready.",
                cross_encoder_path,
            )
            return

        try:
            logger.info("Loading cross-encoder from %s ...", cross_encoder_path)
            self._cross_encoder = CrossEncoder(cross_encoder_path, device=self.device)
            logger.info("Cross-encoder loaded")
        except Exception:
            logger.exception("Failed to load cross-encoder")
            return

        # T5 explanation (optional)
        if os.path.isdir(t5_path):
            try:
                from transformers import pipeline as hf_pipeline
                logger.info("Loading T5 explanation model from %s ...", t5_path)
                self._t5_pipeline = hf_pipeline(
                    "text2text-generation",
                    model=t5_path,
                    device=0 if self.device == "cuda" else -1,
                )
                logger.info("T5 explanation model loaded")
            except Exception:
                logger.warning("T5 model load failed; falling back to template explanations", exc_info=True)
                self._t5_pipeline = None
        else:
            logger.info("T5 model not found at %s; using template explanations", t5_path)

        self._ready = True
        logger.info("CVRankingPipeline is ready")

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

        stage1_candidates = self._stage1_retrieve(job, resumes, top_k)
        stage2_ranked = self._stage2_rerank(job, stage1_candidates, top_n)
        ranked_resumes = self._stage3_explain(
            stage2_ranked, resumes, good_fit_threshold, min_score
        )

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
    # Stage 1 — Bi-Encoder + FAISS
    # ------------------------------------------------------------------

    def _stage1_retrieve(
        self, job: JobInput, resumes: List[ResumeInput], top_k: int
    ) -> List[Dict[str, Any]]:
        """Encode all resumes, build an in-memory FAISS index, query with the job
        embedding, return top_k candidates each carrying similarity_score (0-100)."""

        job_text = _build_job_text(job)
        resume_texts = [_build_resume_text(r) for r in resumes]

        # Encode job as query (E5 prefix: "query: ")
        job_emb = self._bi_encoder.encode(
            f"query: {job_text}",
            normalize_embeddings=True,
            convert_to_numpy=True,
            show_progress_bar=False,
        ).astype(np.float32)

        # Encode all resumes as passages (E5 prefix: "passage: ")
        prefixed = [f"passage: {t}" for t in resume_texts]
        resume_embs = self._bi_encoder.encode(
            prefixed,
            normalize_embeddings=True,
            convert_to_numpy=True,
            batch_size=self.batch_size,
            show_progress_bar=False,
        ).astype(np.float32)

        # Build FAISS inner-product index (vectors already L2-normalised ↔ cosine sim)
        dim = resume_embs.shape[1]
        index = faiss.IndexFlatIP(dim)
        index.add(resume_embs)

        actual_k = min(top_k, len(resumes))
        scores, indices = index.search(job_emb.reshape(1, -1), actual_k)

        candidates = []
        for sim, idx in zip(scores[0], indices[0]):
            if idx < 0:
                continue
            candidates.append({
                "resume_index": resumes[idx].resume_index,
                "_list_index": int(idx),
                "_resume_text": resume_texts[idx],
                "similarity_score": round(float(sim) * 100, 2),
            })

        logger.debug("Stage 1: retrieved %d / %d candidates", len(candidates), len(resumes))
        return candidates

    # ------------------------------------------------------------------
    # Stage 2 — Cross-Encoder reranking
    # ------------------------------------------------------------------

    def _stage2_rerank(
        self, job: JobInput, candidates: List[Dict[str, Any]], top_n: int
    ) -> List[Dict[str, Any]]:
        """Score each candidate with the cross-encoder, compute the fused final_score,
        sort descending and keep top_n."""

        if not candidates:
            return []

        job_text = _build_job_text(job)

        # Build (job_text, resume_text) pairs for cross-encoder
        # _resume_text was stored in Stage 1 from the assembled resume text
        pairs: List[Tuple[str, str]] = [
            (job_text, c.get("_resume_text", "")) for c in candidates
        ]

        raw_scores = self._cross_encoder.predict(
            pairs, convert_to_numpy=True, show_progress_bar=False
        )

        # Sigmoid → probability (0–1) → scale to 0–100
        cross_scores = (1.0 / (1.0 + np.exp(-raw_scores))) * 100.0

        # Score fusion: final = 0.3 * similarity + 0.7 * cross
        for c, cs in zip(candidates, cross_scores):
            c["cross_score"] = round(float(cs), 2)
            c["score"] = round(0.3 * c["similarity_score"] + 0.7 * c["cross_score"], 2)

        # Sort descending by fused score, keep top_n
        ranked = sorted(candidates, key=lambda x: x["score"], reverse=True)[:top_n]
        logger.debug("Stage 2: reranked to top %d", len(ranked))
        return ranked

    # ------------------------------------------------------------------
    # Stage 3 — Explanation + label + min_score filter
    # ------------------------------------------------------------------

    def _stage3_explain(
        self,
        ranked: List[Dict[str, Any]],
        original_resumes: List[ResumeInput],
        good_fit_threshold: float,
        min_score: float,
    ) -> List[RankedResume]:
        """Generate explanation, assign label, filter out score < min_score."""

        resume_by_index = {r.resume_index: r for r in original_resumes}
        result: List[RankedResume] = []

        for c in ranked:
            final_score = c["score"]
            if final_score < min_score:
                continue

            label = "Good Fit" if final_score >= good_fit_threshold else "No Fit"
            resume = resume_by_index.get(c["resume_index"])

            if self._t5_pipeline is not None and resume is not None:
                explanation = self._t5_generate(
                    resume, c["similarity_score"], c["cross_score"], final_score, label
                )
            elif resume is not None:
                explanation = _template_explanation(
                    resume, c["similarity_score"], c["cross_score"], final_score, label
                )
            else:
                explanation = f"Score {final_score:.1f} — {label}."

            result.append(RankedResume(
                resume_index=c["resume_index"],
                score=round(final_score, 2),
                similarity_score=c["similarity_score"],
                cross_score=c["cross_score"],
                label=label,
                explanation=explanation,
            ))

        logger.debug("Stage 3: %d resumes after min_score filter (%.1f)", len(result), min_score)
        return result

    def _t5_generate(
        self,
        resume: ResumeInput,
        similarity_score: float,
        cross_score: float,
        final_score: float,
        label: str,
    ) -> str:
        skills_preview = resume.resume_skills[:100].rstrip(",").strip()
        prompt = (
            f"Resume: {resume.resume_summary[:200]} Skills: {skills_preview} "
            f"Score: {final_score:.1f} Label: {label}. Explain why."
        )
        try:
            out = self._t5_pipeline(prompt, max_new_tokens=80, num_beams=2)
            return out[0]["generated_text"].strip()
        except Exception:
            logger.warning("T5 generation failed; falling back to template", exc_info=True)
            return _template_explanation(resume, similarity_score, cross_score, final_score, label)
