"""
CV Ranking Pipeline — 3-stage inference.

Stage 1: Bi-Encoder + FAISS  → top-K candidates (similarity_score)
Stage 2: Cross-Encoder       → rerank to top-N   (cross_score + final_score)
Stage 3: T5 / rule-based     → natural language explanation + label

Input contract (api_contract.md):
  JobInput   : jd_overview/512, jd_requirements/512, jd_responsibilities/400, jd_preferred/256
  ResumeInput: resume_summary/400, resume_experience/512, resume_skills/300, resume_education/256
  Fields *_text (job_description_text, resume_text): used as fallback when structured fields are empty.

Place trained weights under models/cv-refer/:
  bi-encoder/       — SentenceTransformer bi-encoder (required)
  cross-encoder/    — CrossEncoder sentence-transformers (optional; degrades to bi-encoder)
  explanation-t5/   — T5 seq2seq AutoModelForSeq2SeqLM (optional; degrades to rule-based)

Score fusion:
  final_score = 0.3 * similarity_score + 0.7 * cross_score   (all scores 0–100)

Labels (configurable via good_fit_threshold; calibrated optimum ~27):
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
from dataclasses import dataclass
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
    job_description_text: str = ""  # fallback when structured fields empty


@dataclass
class ResumeInput:
    resume_index: int
    resume_summary: str
    resume_experience: str
    resume_skills: str
    resume_education: str = ""
    resume_text: str = ""  # fallback when structured fields empty


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
# Text assembly helpers (section labels + hard char limits per spec)
# ---------------------------------------------------------------------------

def _build_job_text(job: JobInput) -> str:
    parts = []
    if job.jd_overview:
        parts.append(f"Overview: {job.jd_overview[:512]}")
    if job.jd_requirements:
        parts.append(f"Requirements: {job.jd_requirements[:512]}")
    if job.jd_responsibilities:
        parts.append(f"Responsibilities: {job.jd_responsibilities[:400]}")
    if job.jd_preferred:
        parts.append(f"Preferred: {job.jd_preferred[:256]}")
    text = " | ".join(parts).strip()
    # Fallback: use raw description when structured fields were not populated
    if not text:
        text = (job.job_description_text or "")[:450].strip()
    return text


def _build_resume_text(resume: ResumeInput) -> str:
    parts = []
    if resume.resume_summary:
        parts.append(f"Summary: {resume.resume_summary[:400]}")
    if resume.resume_experience:
        parts.append(f"Experience: {resume.resume_experience[:512]}")
    if resume.resume_skills:
        parts.append(f"Skills: {resume.resume_skills[:300]}")
    if resume.resume_education:
        parts.append(f"Education: {resume.resume_education[:256]}")
    text = " | ".join(parts).strip()
    # Fallback: use raw resume text when structured fields were not populated
    if not text:
        text = (resume.resume_text or "")[:450].strip()
    return text


# ---------------------------------------------------------------------------
# Explanation helpers (module-level, no model state)
# ---------------------------------------------------------------------------

def _t5_generate(model, tokenizer, job_text: str, resume_text: str, score: float) -> str:
    import torch
    # Format must match training: "rank job: {jd} | resume: {cv} | score: {N}"
    prompt = (
        f"rank job: {job_text[:450]} | "
        f"resume: {resume_text[:450]} | "
        f"score: {score:.0f}"
    )
    inputs = tokenizer(prompt, return_tensors="pt", max_length=512, truncation=True)
    with torch.no_grad():
        outputs = model.generate(**inputs, max_new_tokens=128, num_beams=4, early_stopping=True)
    return tokenizer.decode(outputs[0], skip_special_tokens=True)


def _rule_explanation(score: float) -> str:
    if score >= 75:
        return "Strong match across skills, experience, and qualifications for this role."
    if score >= 55:
        return "Good alignment with key requirements; meets most criteria for this position."
    if score >= 35:
        return "Partial match; has some relevant skills but may lack key qualifications."
    return "Limited alignment with the job requirements based on profile analysis."


def _is_valid_t5_explanation(text: str) -> bool:
    """
    Reject T5 output that is empty, too short, or contains error/diagnostic patterns
    from a bad or undertrained model. Falls back to rule-based in those cases.
    """
    if not text or len(text.strip()) < 15:
        return False
    lower = text.lower()
    # Reject outputs that look like internal error messages or diagnostic artifacts
    return not any(kw in lower for kw in ("failed", "error", "cross-encoder", "llm", "score ("))


# ---------------------------------------------------------------------------
# Pipeline
# ---------------------------------------------------------------------------

class CVRankingPipeline:
    """
    Three-stage CV ranking pipeline.

    Bi-encoder and cross-encoder are required; T5 explanation model is optional
    (falls back to rule-based explanations when absent).
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
        self.good_fit_threshold_default: float = config.get("good_fit_threshold", 27.0)

        self._bi_encoder: Optional[SentenceTransformer] = None
        self._cross_encoder: Optional[CrossEncoder] = None
        self._t5_model = None
        self._t5_tokenizer = None
        self._ready = False

        self._load_models()

    def _load_models(self):
        bi_path = os.path.join(self.model_dir, self.config.get("bi_encoder_model", "bi-encoder"))
        ce_path = os.path.join(self.model_dir, self.config.get("cross_encoder_model", "cross-encoder"))
        t5_path = os.path.join(self.model_dir, self.config.get("t5_model", "explanation-t5"))

        # Bi-encoder — required
        try:
            self._bi_encoder = SentenceTransformer(bi_path, device=self.device)
            self._bi_encoder.eval()
            logger.info("CVRankingPipeline: bi-encoder loaded from %s", bi_path)
        except Exception as e:
            logger.error(
                "CVRankingPipeline: bi-encoder failed (%s) — endpoint will return 503.", e
            )
            return

        # Cross-encoder — optional
        try:
            self._cross_encoder = CrossEncoder(ce_path, device=self.device)
            logger.info("CVRankingPipeline: cross-encoder loaded from %s", ce_path)
        except Exception as e:
            logger.warning("CVRankingPipeline: cross-encoder not loaded (%s) — stage 2 uses bi-encoder scores.", e)

        # T5 explanation — optional
        try:
            from transformers import AutoTokenizer, AutoModelForSeq2SeqLM
            self._t5_tokenizer = AutoTokenizer.from_pretrained(t5_path)
            self._t5_model = AutoModelForSeq2SeqLM.from_pretrained(t5_path)
            self._t5_model.eval()
            logger.info("CVRankingPipeline: T5 explanation model loaded from %s", t5_path)
        except Exception as e:
            logger.warning("CVRankingPipeline: T5 not loaded (%s) — explanations will be rule-based.", e)

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
            resume_embeddings: Optional[List[Optional[np.ndarray]]] = None,
    ) -> RankResult:
        """
        resume_embeddings, when given, must be the same length as resumes — one
        precomputed bi-encoder embedding per resume (None entries fall back to
        encoding on the fly). Lets callers with a persistent embedding cache
        (rank-by-job) skip re-encoding resumes that haven't changed since their
        last CV snapshot, while the raw /rank endpoint (no cache) keeps encoding
        every resume on every call as before.
        """
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
        stage1 = self._stage1_retrieve(job, resumes, top_k, resume_embeddings)
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
    # Stage 1 — Bi-Encoder + FAISS
    # ------------------------------------------------------------------

    def _resolve_resume_embeddings(
            self,
            resumes: List[ResumeInput],
            resume_embeddings: Optional[List[Optional[np.ndarray]]],
    ) -> np.ndarray:
        """
        Fill in any missing entries in resume_embeddings by batch-encoding just
        those resumes, then stack everything back into resumes' original order.
        With no precomputed embeddings at all (resume_embeddings=None), this is
        equivalent to encoding every resume — i.e. unchanged behavior for the
        raw /rank endpoint.
        """
        if resume_embeddings is None:
            resume_embeddings = [None] * len(resumes)

        missing_idx = [i for i, emb in enumerate(resume_embeddings) if emb is None]
        if missing_idx:
            texts = [f"passage: {_build_resume_text(resumes[i])}" for i in missing_idx]
            encoded = self._bi_encoder.encode(
                texts,
                normalize_embeddings=True,
                convert_to_numpy=True,
                batch_size=self.batch_size,
                show_progress_bar=False,
            ).astype(np.float32)
            for i, emb in zip(missing_idx, encoded):
                resume_embeddings[i] = emb

        return np.stack(resume_embeddings).astype(np.float32)

    def encode_resume(self, resume: ResumeInput) -> np.ndarray:
        """
        Encode a single resume into the bi-encoder embedding space (same
        normalization/prefix as stage 1). Used to precompute and cache an
        embedding when a CV snapshot is written, instead of at rank time.
        """
        resume_text = _build_resume_text(resume)
        return self._bi_encoder.encode(
            f"passage: {resume_text}",
            normalize_embeddings=True,
            convert_to_numpy=True,
            show_progress_bar=False,
        ).astype(np.float32)

    def _stage1_retrieve(
            self,
            job: JobInput,
            resumes: List[ResumeInput],
            top_k: int,
            resume_embeddings: Optional[List[Optional[np.ndarray]]] = None,
    ) -> List[Dict[str, Any]]:
        """Encode job + resumes, FAISS inner-product search, return top_k with similarity_score (0-100)."""
        job_text = _build_job_text(job)

        job_emb = self._bi_encoder.encode(
            f"query: {job_text}",
            normalize_embeddings=True,
            convert_to_numpy=True,
            show_progress_bar=False,
        ).astype(np.float32)

        resume_embs = self._resolve_resume_embeddings(resumes, resume_embeddings)

        index = faiss.IndexFlatIP(resume_embs.shape[1])
        index.add(resume_embs)
        scores, indices = index.search(job_emb.reshape(1, -1), min(top_k, len(resumes)))

        candidates = []
        for sim, idx in zip(scores[0], indices[0]):
            if idx < 0:
                continue
            candidates.append({
                "resume_index": resumes[idx].resume_index,
                "_resume": resumes[idx],
                "_job_text": job_text,
                "similarity_score": round(float(sim) * 100, 2),
                "score": round(float(sim) * 100, 2),
            })

        logger.debug("Stage 1: %d / %d candidates", len(candidates), len(resumes))
        return candidates

    # ------------------------------------------------------------------
    # Stage 2 — Cross-Encoder reranking
    # ------------------------------------------------------------------

    def _stage2_rerank(
            self, job: JobInput, candidates: List[Dict[str, Any]], top_n: int
    ) -> List[Dict[str, Any]]:
        """Cross-encoder reranking with score fusion (0.3 sim + 0.7 cross). Falls back to bi-encoder order."""
        if not candidates:
            return []

        if self._cross_encoder is None:
            for c in candidates:
                c["cross_score"] = c["similarity_score"]
            return candidates[:top_n]

        job_text = candidates[0].get("_job_text", _build_job_text(job))
        pairs: List[Tuple[str, str]] = [
            (job_text, _build_resume_text(c["_resume"])) for c in candidates
        ]

        try:
            raw = self._cross_encoder.predict(pairs, convert_to_numpy=True, show_progress_bar=False)
            cross_scores = (1.0 / (1.0 + np.exp(-raw))) * 100.0

            for c, cs in zip(candidates, cross_scores):
                c["cross_score"] = round(float(cs), 2)
                c["score"] = round(0.3 * c["similarity_score"] + 0.7 * c["cross_score"], 2)

            candidates.sort(key=lambda c: c["score"], reverse=True)
        except Exception as e:
            logger.warning("Stage 2 cross-encoder failed (%s); using bi-encoder scores.", e)
            for c in candidates:
                c["cross_score"] = c["similarity_score"]

        logger.debug("Stage 2: reranked to top %d", top_n)
        return candidates[:top_n]

    # ------------------------------------------------------------------
    # Stage 3 — Explanation + label + min_score filter
    # ------------------------------------------------------------------

    def _stage3_explain(
            self,
            ranked: List[Dict[str, Any]],
            good_fit_threshold: float,
            min_score: float,
    ) -> List[RankedResume]:
        """Assign label, generate explanation, filter score < min_score."""
        results = []
        for c in ranked:
            score = c["score"]
            if score < min_score:
                continue
            label = "Good Fit" if score >= good_fit_threshold else "No Fit"
            results.append(RankedResume(
                resume_index=c["resume_index"],
                score=round(score, 2),
                similarity_score=c["similarity_score"],
                cross_score=c.get("cross_score", c["similarity_score"]),
                label=label,
                explanation=self._explain(c, label),
            ))
        logger.debug("Stage 3: %d resumes after min_score filter (%.1f)", len(results), min_score)
        return results

    def _explain(self, candidate: Dict[str, Any], label: str) -> str:
        job_text = candidate.get("_job_text", "")
        resume_text = _build_resume_text(candidate["_resume"])
        score = candidate["score"]

        # If either side has no content, skip T5 — rule-based is more honest than hallucination
        if not job_text or not resume_text:
            logger.debug(
                "Skipping T5 for resume_index=%s (job_text=%d chars, resume_text=%d chars); using rule-based.",
                candidate.get("resume_index"), len(job_text), len(resume_text),
            )
            return _rule_explanation(score)

        if self._t5_model is not None:
            try:
                t5_text = _t5_generate(self._t5_model, self._t5_tokenizer, job_text, resume_text, score)
                if _is_valid_t5_explanation(t5_text):
                    return t5_text
                logger.warning(
                    "T5 generated invalid/garbage explanation for resume_index=%s (output=%r); using rule-based fallback.",
                    candidate.get("resume_index"), t5_text,
                )
            except Exception as e:
                logger.warning("T5 generation failed (%s); using rule-based fallback.", e)
        return _rule_explanation(score)
