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
import re
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
    match_points: List[str] = None   # parsed from T5 output "match: X; Y."
    miss_points: List[str] = None    # parsed from T5 output "miss: A; B."
    input_coverage: float = 1.0      # fraction of CV fields that are non-empty
    fields_used: Dict[str, bool] = None  # per-field presence mask

    def __post_init__(self):
        if self.match_points is None:
            self.match_points = []
        if self.miss_points is None:
            self.miss_points = []
        if self.fields_used is None:
            self.fields_used = {}


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
    # Always append full description when structured fields are absent or thin —
    # job APIs often only return description without separate requirements/responsibilities keys.
    structured_len = sum(
        len(p) for p in [job.jd_requirements, job.jd_responsibilities, job.jd_preferred] if p
    )
    if job.job_description_text and structured_len < 100:
        parts.append(job.job_description_text[:600])
    return " | ".join(parts).strip()


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
        outputs = model.generate(
            **inputs,
            max_new_tokens=128,
            num_beams=4,
            early_stopping=True,
            no_repeat_ngram_size=3,
            repetition_penalty=1.5,
        )
    return tokenizer.decode(outputs[0], skip_special_tokens=True)


def _rule_explanation(score: float) -> str:
    if score >= 75:
        return "Strong match across skills, experience, and qualifications for this role."
    if score >= 55:
        return "Good alignment with key requirements; meets most criteria for this position."
    if score >= 35:
        return "Partial match; has some relevant skills but may lack key qualifications."
    return "Limited alignment with the job requirements based on profile analysis."


def _build_nl_explanation(match_points: list, miss_points: list, label: str, score: float) -> str:
    """Convert structured match/miss lists into a readable natural language explanation."""
    parts = []
    if match_points:
        parts.append(f"Matches: {', '.join(match_points)}.")
    if miss_points:
        parts.append(f"Missing: {', '.join(miss_points)}.")
    if parts:
        return " ".join(parts)
    return _rule_explanation(score)


_MAX_POINT_LEN = 80  # discard any parsed point longer than this (repetition artifact)


def _parse_match_miss(text: str) -> tuple:
    """Parse T5 output 'match: X; Y. miss: A; B.' into two lists."""
    m = re.search(r'match:\s*([^.]+)', text, re.IGNORECASE)
    x = re.search(r'miss:\s*([^.]+)',  text, re.IGNORECASE)
    match_points = [
        s.strip() for s in m.group(1).split(';')
        if s.strip() and len(s.strip()) <= _MAX_POINT_LEN
    ] if m else []
    miss_points = [
        s.strip() for s in x.group(1).split(';')
        if s.strip() and len(s.strip()) <= _MAX_POINT_LEN
    ] if x else []
    return match_points, miss_points


# Tech skill tokens — longest-first to avoid substring shadowing
_SKILL_RE = re.compile(
    r'\b('
    r'next\.?js|nest\.?js|node\.?js|vue\.?js|react\.?js|react|angular|svelte|'
    r'typescript|javascript|python|java\b|golang|rust\b|kotlin|swift|dart|php|ruby|scala|'
    r'spring\s*boot|spring|django|flask|fastapi|express\.?js|laravel|rails|'
    r'postgresql|mysql|mongodb|redis|elasticsearch|cassandra|dynamodb|sqlite|'
    r'docker|kubernetes|k8s|terraform|ansible|jenkins|github\s*actions|gitlab\s*ci|'
    r'aws|gcp|azure|cloud|devops|ci/?cd|'
    r'graphql|rest\s*api|grpc|kafka|rabbitmq|celery|'
    r'pytorch|tensorflow|keras|scikit.learn|pandas|numpy|'
    r'git|linux|bash|sql|html|css|'
    r'microservices|monorepo|agile|scrum'
    r')\b',
    re.IGNORECASE,
)

# Education level patterns
_EDU_RE = re.compile(
    r'\b(bachelor\'?s?(?:\s+(?:degree|of\s+\w+))?'
    r'|master\'?s?(?:\s+(?:degree|of\s+\w+))?'
    r'|ph\.?d\.?|mba'
    r'|(?:degree\s+in\s+[\w\s]{3,30}))',
    re.IGNORECASE,
)

# Experience year patterns
_EXP_RE = re.compile(
    r'\b(\d+\+?\s*(?:years?|yrs?)\s+(?:of\s+)?(?:[\w]+\s+){0,4}experience)',
    re.IGNORECASE,
)

# Soft / domain skills
_SOFT_RE = re.compile(
    r'\b(communication\s+skills?|leadership|teamwork|problem[\s-]solving'
    r'|analytical|critical\s+thinking|time\s+management|attention\s+to\s+detail'
    r'|collaboration|presentation\s+skills?|project\s+management)',
    re.IGNORECASE,
)


_STOP_WORDS = frozenset({
    'with', 'the', 'and', 'for', 'that', 'this', 'from', 'have', 'has',
    'are', 'was', 'will', 'can', 'may', 'must', 'not', 'but', 'its',
    'our', 'you', 'your', 'their', 'such', 'than', 'into', 'over',
    'able', 'well', 'good', 'high', 'new', 'use', 'used', 'using',
    'also', 'some', 'all', 'any', 'each', 'which', 'they', 'them',
})

_SKILL_RE = re.compile(
    r'\b('
    r'next\.?js|nest\.?js|node\.?js|vue\.?js|react\.?js|react|angular|svelte|'
    r'typescript|javascript|python|java\b|golang|rust\b|kotlin|swift|dart|php|ruby|scala|'
    r'spring\s*boot|spring|django|flask|fastapi|express\.?js|laravel|rails|'
    r'postgresql|mysql|mongodb|redis|elasticsearch|cassandra|dynamodb|sqlite|'
    r'docker|kubernetes|k8s|terraform|ansible|jenkins|github\s*actions|gitlab\s*ci|'
    r'aws|gcp|azure|cloud|devops|ci/?cd|'
    r'graphql|rest\s*api|grpc|kafka|rabbitmq|celery|'
    r'pytorch|tensorflow|keras|scikit.learn|pandas|numpy|'
    r'git|linux|bash|sql|html|css|'
    r'microservices|monorepo|agile|scrum|'
    r'\d+\+?\s*(?:years?|yrs?)\s+(?:\w+\s+){0,3}experience'
    r')\b',
    re.IGNORECASE,
)


_SECTION_HEADER_RE = re.compile(
    r'^(what you\'?ll do|responsibilities|requirements|qualifications|about|overview'
    r'|preferred|benefits|nice to have|you will|your role|the role|join us)',
    re.IGNORECASE,
)


def _extract_jd_phrases(job_text: str) -> list:
    """
    Split JD text into natural language requirement phrases.
    Filters out section headers and short/empty fragments.
    """
    # Split on bullet chars and newlines only — NOT on hyphens (would break "stand-ups", "back-end" etc.)
    parts = re.split(r'[•\*\n;]|(?<!\d),(?!\d)', job_text)
    phrases = []
    seen = set()
    for p in parts:
        p = re.sub(r'\s+', ' ', p).strip(' .,|')
        # Strip leading bullet-style hyphens only
        p = re.sub(r'^[\-–]+\s*', '', p).strip()
        if not (8 <= len(p) <= 100):
            continue
        if re.match(r'^(and|or|to|but|with|also)\s', p, re.IGNORECASE):
            continue
        if p.endswith(':') or _SECTION_HEADER_RE.match(p):
            continue
        words = [w for w in re.findall(r'\b[a-zA-Z]\w{2,}\b', p.lower())
                 if w not in _STOP_WORDS]
        if len(words) < 2:
            continue
        key = p.lower()
        if key not in seen:
            seen.add(key)
            phrases.append(p)
    return phrases[:25]


def _split_cv_sentences(text: str) -> list:
    """Split CV field text into individual sentences/clauses."""
    parts = re.split(r'[.\n;]', text or "")
    return [p.strip() for p in parts if len(p.strip()) >= 12]


def _find_cv_phrase(keywords: list, cv_fields: dict, max_len: int = 100) -> str:
    """
    Find the CV sentence that best demonstrates the given keywords.
    Searches all 4 CV fields. Returns the best matching sentence truncated.
    """
    best_sent, best_score = "", 0
    for field_text in cv_fields.values():
        for sent in _split_cv_sentences(field_text):
            sent_lower = sent.lower()
            score = sum(1 for kw in keywords if kw in sent_lower)
            if score > best_score:
                best_score = score
                best_sent = sent
    return best_sent[:max_len].strip() if best_score > 0 else ""


def _rule_match_miss(
    job_text: str,
    resume: "ResumeInput",
    max_match: int = 4,
    max_miss: int = 3,
) -> tuple:
    """
    Match/miss extraction:
    - Step 1: tech skill matching via _SKILL_RE → clean skill name tokens
    - Step 2: phrase-level matching for remaining slots (non-tech requirements)
    matchPoints show concrete skills/phrases from CV; missPoints show JD gaps.
    """
    cv_fields = {
        "skills":     getattr(resume, "resume_skills",     "") or "",
        "experience": getattr(resume, "resume_experience", "") or "",
        "summary":    getattr(resume, "resume_summary",    "") or "",
        "education":  getattr(resume, "resume_education",  "") or "",
    }
    cv_full = " ".join(cv_fields.values()).lower()

    match_points, miss_points = [], []

    # Step 1: tech skill matching — precise, returns clean skill tokens
    jd_skills = list(dict.fromkeys(
        m.group(0).strip() for m in _SKILL_RE.finditer(job_text)
    ))
    for skill in jd_skills:
        needle = re.sub(r'[\s.]+', r'[\\s.]*', re.escape(skill))
        if re.search(needle, cv_full, re.IGNORECASE):
            if len(match_points) < max_match:
                match_points.append(skill)
        else:
            if len(miss_points) < max_miss:
                miss_points.append(skill)
        if len(match_points) >= max_match and len(miss_points) >= max_miss:
            break

    # Step 2: phrase-level matching for remaining slots
    if len(match_points) < max_match or len(miss_points) < max_miss:
        jd_phrases = _extract_jd_phrases(job_text)
        used_cv_sentences = set()
        for phrase in jd_phrases:
            keywords = [w for w in re.findall(r'\b[a-zA-Z]\w{2,}\b', phrase.lower())
                        if w not in _STOP_WORDS]
            if not keywords:
                continue
            found = sum(1 for kw in keywords if kw in cv_full)
            threshold = 1 if len(keywords) <= 3 else max(2, int(len(keywords) * 0.5))
            if found >= threshold:
                if len(match_points) < max_match:
                    cv_phrase = _find_cv_phrase(keywords, cv_fields)
                    if cv_phrase and cv_phrase not in used_cv_sentences:
                        used_cv_sentences.add(cv_phrase)
                        match_points.append(cv_phrase)
            else:
                if len(miss_points) < max_miss:
                    miss_points.append(phrase)

    return match_points, miss_points


def _field_coverage(resume: ResumeInput) -> tuple:
    """Return (coverage_ratio, fields_dict) for the 4 CV structured fields."""
    fields = {
        "resume_summary":    bool((resume.resume_summary    or "").strip()),
        "resume_experience": bool((resume.resume_experience or "").strip()),
        "resume_skills":     bool((resume.resume_skills     or "").strip()),
        "resume_education":  bool((resume.resume_education  or "").strip()),
    }
    coverage = sum(fields.values()) / len(fields)
    return round(coverage, 3), fields


def _is_valid_t5_explanation(text: str) -> bool:
    """
    Reject T5 output that is empty, too short, contains error patterns,
    or shows repetition loops (e.g. "Containerized Containerized...").
    """
    if not text or len(text.strip()) < 15:
        return False
    lower = text.lower()
    if any(kw in lower for kw in ("failed", "error", "cross-encoder", "llm", "score (")):
        return False
    # Detect repetition: any single word appearing > 4 times → garbage output
    words = lower.split()
    if words:
        from collections import Counter
        if max(Counter(words).values()) > 4:
            return False
    return True


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
                    "resume_index":    r.resume_index,
                    "score":           r.score,
                    "similarity_score":r.similarity_score,
                    "cross_score":     r.cross_score,
                    "label":           r.label,
                    "explanation":     r.explanation,
                    "match_points":    r.match_points,
                    "miss_points":     r.miss_points,
                    "input_coverage":  r.input_coverage,
                    "fields_used":     r.fields_used,
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
            raw = np.array(raw, dtype=np.float32)
            r_min, r_max = raw.min(), raw.max()
            if r_max - r_min > 0.5:
                # Enough variance in batch → min-max normalize to [5, 95]
                cross_scores = 5.0 + (raw - r_min) / (r_max - r_min) * 90.0
            else:
                # Batch too uniform → fallback to sigmoid
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
        """Assign label, generate explanation + match/miss, filter score < min_score."""
        results = []
        for c in ranked:
            score = c["score"]
            if score < min_score:
                continue
            label = "Good Fit" if score >= good_fit_threshold else "No Fit"
            explanation = self._explain(c, label)

            # If T5 produced structured format ("miss: X; Y."), it's not a readable explanation.
            # Fall back to score-based narrative — match/miss arrays carry the detail separately.
            t5_match, t5_miss = _parse_match_miss(explanation)
            if t5_match or t5_miss:
                explanation = _rule_explanation(score)

            # match/miss always from rule-based — checks actual CV text, no hallucination
            match_points, miss_points = _rule_match_miss(
                c.get("_job_text", ""), c.get("_resume"),
            )
            # fallback: JD has no parseable requirements → use T5's extraction
            if not match_points and not miss_points:
                match_points, miss_points = t5_match, t5_miss

            resume = c.get("_resume", {})
            coverage, fields_used = _field_coverage(resume)

            results.append(RankedResume(
                resume_index=c["resume_index"],
                score=round(score, 2),
                similarity_score=c["similarity_score"],
                cross_score=c.get("cross_score", c["similarity_score"]),
                label=label,
                explanation=explanation,
                match_points=match_points,
                miss_points=miss_points,
                input_coverage=coverage,
                fields_used=fields_used,
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
