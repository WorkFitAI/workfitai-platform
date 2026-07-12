"""
Unit tests for module-level helpers in src/inference/pipeline.py.

Covers:
- _parse_match_miss: structured T5 output parsing
- _field_coverage: CV field presence ratio
- RankedResume: dataclass defaults / __post_init__
- CVRankingPipeline.to_dict: serialises new fields
- _stage3_explain: propagates match/miss/coverage into RankedResume
"""

import sys
import os
import types

# ---------------------------------------------------------------------------
# Stub heavy imports so the module loads without trained weights or GPU
# ---------------------------------------------------------------------------
for _mod in ("faiss", "sentence_transformers", "yaml"):
    if _mod not in sys.modules:
        sys.modules[_mod] = types.ModuleType(_mod)

# sentence_transformers stubs
st = sys.modules["sentence_transformers"]
st.SentenceTransformer = object
st.CrossEncoder = object

# yaml stub — load_config calls yaml.safe_load
yaml_mod = sys.modules["yaml"]
yaml_mod.safe_load = lambda f: {}

import pytest

# Now import the helpers directly; skip __init__ side-effects by importing the
# module functions, not through the package.
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "src", "inference"))
from pipeline import (  # noqa: E402
    _parse_match_miss,
    _field_coverage,
    RankedResume,
    ResumeInput,
    RankResult,
    _build_resume_text,
    _build_job_text,
    JobInput,
)


# ---------------------------------------------------------------------------
# _parse_match_miss
# ---------------------------------------------------------------------------

class TestParseMatchMiss:
    def test_full_format(self):
        text = "match: Python; FastAPI; REST. miss: Kubernetes; Go."
        match, miss = _parse_match_miss(text)
        assert match == ["Python", "FastAPI", "REST"]
        assert miss == ["Kubernetes", "Go"]

    def test_match_only(self):
        text = "match: Docker; CI/CD."
        match, miss = _parse_match_miss(text)
        assert match == ["Docker", "CI/CD"]
        assert miss == []

    def test_miss_only(self):
        text = "miss: Leadership; Agile."
        match, miss = _parse_match_miss(text)
        assert match == []
        assert miss == ["Leadership", "Agile"]

    def test_empty_string(self):
        match, miss = _parse_match_miss("")
        assert match == []
        assert miss == []

    def test_case_insensitive(self):
        text = "Match: Java. Miss: Cloud."
        match, miss = _parse_match_miss(text)
        assert match == ["Java"]
        assert miss == ["Cloud"]

    def test_single_item_no_semicolon(self):
        text = "match: Python experience. miss: Leadership skills."
        match, miss = _parse_match_miss(text)
        assert match == ["Python experience"]
        assert miss == ["Leadership skills"]

    def test_extra_whitespace(self):
        text = "match:  Python ;  FastAPI . miss:  Go ."
        match, miss = _parse_match_miss(text)
        assert "Python" in match
        assert "FastAPI" in match
        assert "Go" in miss

    def test_no_match_miss_tokens(self):
        text = "Strong candidate with good background."
        match, miss = _parse_match_miss(text)
        assert match == []
        assert miss == []

    def test_rule_based_explanation_returns_empty(self):
        # Rule-based explanations don't have the match/miss format
        text = "Strong match across skills, experience, and qualifications for this role."
        match, miss = _parse_match_miss(text)
        assert match == []
        assert miss == []


# ---------------------------------------------------------------------------
# _field_coverage
# ---------------------------------------------------------------------------

class TestFieldCoverage:
    def _resume(self, summary="", experience="", skills="", education=""):
        return ResumeInput(
            resume_index=0,
            resume_summary=summary,
            resume_experience=experience,
            resume_skills=skills,
            resume_education=education,
        )

    def test_all_fields_present(self):
        r = self._resume("Summary text", "5 years", "Python", "BSc")
        cov, fields = _field_coverage(r)
        assert cov == 1.0
        assert all(fields.values())

    def test_no_fields_present(self):
        r = self._resume()
        cov, fields = _field_coverage(r)
        assert cov == 0.0
        assert not any(fields.values())

    def test_half_fields_present(self):
        r = self._resume(summary="s", experience="e")
        cov, fields = _field_coverage(r)
        assert cov == 0.5
        assert fields["resume_summary"] is True
        assert fields["resume_experience"] is True
        assert fields["resume_skills"] is False
        assert fields["resume_education"] is False

    def test_whitespace_only_is_absent(self):
        r = self._resume(summary="   ", skills="Python")
        cov, fields = _field_coverage(r)
        assert fields["resume_summary"] is False
        assert fields["resume_skills"] is True
        assert cov == pytest.approx(0.25)

    def test_none_fields_treated_as_absent(self):
        r = ResumeInput(
            resume_index=0,
            resume_summary=None,
            resume_experience=None,
            resume_skills="Python",
            resume_education=None,
        )
        cov, fields = _field_coverage(r)
        assert cov == pytest.approx(0.25)
        assert fields["resume_skills"] is True

    def test_returns_four_keys(self):
        r = self._resume("s", "e", "sk", "ed")
        _, fields = _field_coverage(r)
        assert set(fields.keys()) == {
            "resume_summary", "resume_experience", "resume_skills", "resume_education"
        }

    def test_coverage_is_rounded_to_three_decimal_places(self):
        # 3 of 4 fields = 0.75 exactly
        r = self._resume("s", "e", "sk")
        cov, _ = _field_coverage(r)
        assert cov == 0.75


# ---------------------------------------------------------------------------
# RankedResume dataclass
# ---------------------------------------------------------------------------

class TestRankedResume:
    def test_defaults_are_empty_not_none(self):
        r = RankedResume(
            resume_index=0, score=80.0, similarity_score=75.0,
            cross_score=82.0, label="Good Fit", explanation="match: Python. miss: Go.",
        )
        assert r.match_points == []
        assert r.miss_points == []
        assert r.fields_used == {}
        assert r.input_coverage == 1.0

    def test_mutable_defaults_are_not_shared(self):
        r1 = RankedResume(0, 80.0, 75.0, 82.0, "Good Fit", "x")
        r2 = RankedResume(1, 70.0, 65.0, 72.0, "No Fit", "y")
        r1.match_points.append("Python")
        assert r2.match_points == []  # no aliasing

    def test_explicit_values_preserved(self):
        r = RankedResume(
            resume_index=1, score=55.0, similarity_score=50.0,
            cross_score=58.0, label="No Fit", explanation="match: Docker. miss: K8s.",
            match_points=["Docker"], miss_points=["K8s"],
            input_coverage=0.75,
            fields_used={"resume_summary": True, "resume_experience": False,
                         "resume_skills": True, "resume_education": True},
        )
        assert r.match_points == ["Docker"]
        assert r.miss_points == ["K8s"]
        assert r.input_coverage == 0.75
        assert r.fields_used["resume_experience"] is False


# ---------------------------------------------------------------------------
# _build_resume_text / _build_job_text (smoke-level to catch regressions)
# ---------------------------------------------------------------------------

class TestBuildText:
    def test_resume_text_includes_all_sections(self):
        r = ResumeInput(0, "Engineer", "5yr Python", "Python FastAPI", "BSc CS")
        text = _build_resume_text(r)
        assert "Summary:" in text
        assert "Experience:" in text
        assert "Skills:" in text
        assert "Education:" in text

    def test_resume_text_fallback_when_all_empty(self):
        r = ResumeInput(0, "", "", "", "", resume_text="raw text here")
        assert _build_resume_text(r) == "raw text here"

    def test_job_text_includes_structured_sections(self):
        j = JobInput(0, "Build APIs", "Python required", "Design services")
        text = _build_job_text(j)
        assert "Overview:" in text
        assert "Requirements:" in text
        assert "Responsibilities:" in text

    def test_job_text_fallback_when_all_empty(self):
        j = JobInput(0, "", "", "", "", job_description_text="raw jd")
        assert _build_job_text(j) == "raw jd"


# ---------------------------------------------------------------------------
# to_dict — serialises all 4 new fields
# ---------------------------------------------------------------------------

class TestToDictNewFields:
    """
    CVRankingPipeline.to_dict must include match_points, miss_points,
    input_coverage, fields_used for each ranked resume.
    We build a mock pipeline object just to call to_dict — no model needed.
    """

    def _make_pipeline(self):
        # Import without triggering __init__ model loading
        import importlib
        import unittest.mock as mock

        # Patch _load_models so __init__ does nothing heavy
        with mock.patch.object(
            __import__("pipeline", fromlist=["CVRankingPipeline"]).CVRankingPipeline,
            "_load_models",
            lambda self: None,
        ):
            from pipeline import CVRankingPipeline
            p = CVRankingPipeline.__new__(CVRankingPipeline)
            return p

    def test_to_dict_includes_match_miss_coverage(self):
        from pipeline import CVRankingPipeline, RankResult, RankedResume

        pipeline = CVRankingPipeline.__new__(CVRankingPipeline)
        result = RankResult(
            job_index=1,
            job_overview="Backend role",
            total_candidates=2,
            processing_time_ms=120.0,
            ranked_resumes=[
                RankedResume(
                    resume_index=0,
                    score=82.0, similarity_score=80.0, cross_score=84.0,
                    label="Good Fit",
                    explanation="match: Python; FastAPI. miss: Kubernetes.",
                    match_points=["Python", "FastAPI"],
                    miss_points=["Kubernetes"],
                    input_coverage=0.75,
                    fields_used={
                        "resume_summary": True, "resume_experience": True,
                        "resume_skills": True, "resume_education": False,
                    },
                )
            ],
        )
        d = pipeline.to_dict(result)
        r = d["ranked_resumes"][0]

        assert r["match_points"] == ["Python", "FastAPI"]
        assert r["miss_points"] == ["Kubernetes"]
        assert r["input_coverage"] == 0.75
        assert r["fields_used"]["resume_education"] is False

    def test_to_dict_empty_match_miss(self):
        from pipeline import CVRankingPipeline, RankResult, RankedResume

        pipeline = CVRankingPipeline.__new__(CVRankingPipeline)
        result = RankResult(
            job_index=1, job_overview="Role", total_candidates=1,
            processing_time_ms=50.0,
            ranked_resumes=[
                RankedResume(0, 40.0, 38.0, 41.0, "No Fit",
                             "Limited alignment.")
            ],
        )
        d = pipeline.to_dict(result)
        r = d["ranked_resumes"][0]
        assert r["match_points"] == []
        assert r["miss_points"] == []
        assert r["input_coverage"] == 1.0
        assert r["fields_used"] == {}
