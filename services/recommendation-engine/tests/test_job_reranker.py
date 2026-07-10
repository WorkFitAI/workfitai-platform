"""
Tests for app/services/job_reranker.py (JobReranker).

CrossEncoder is mocked at the constructor boundary — the reranking decision
logic (pairing, sigmoid, sort, rank assignment, fallback on failure) is what's
under test here, not the real cross-encoder model.
"""

from unittest.mock import MagicMock

import numpy as np
import pytest

from app.services.job_reranker import JobReranker


@pytest.fixture
def reranker(monkeypatch):
    fake_model = MagicMock()
    monkeypatch.setattr("app.services.job_reranker.CrossEncoder", lambda path: fake_model)
    r = JobReranker("/fake/cross-encoder")
    r._fake_model = fake_model
    return r


def _candidate(job_id, score, **overrides):
    c = {
        "jobId": job_id, "score": score, "title": "Backend Engineer",
        "company": "Acme", "location": "Remote", "skills": ["Python", "SQL"],
        "description": "Build APIs", "shortDescription": "Great role",
        "requirements": "Python required", "responsibilities": "Ship features",
        "benefits": "Remote work",
    }
    c.update(overrides)
    return c


class TestConstruction:
    def test_load_failure_raises(self, monkeypatch):
        def _raise(path):
            raise OSError("model missing")

        monkeypatch.setattr("app.services.job_reranker.CrossEncoder", _raise)
        with pytest.raises(OSError):
            JobReranker("/missing/cross-encoder")


class TestRerank:
    def test_no_candidates_returns_empty(self, reranker):
        assert reranker.rerank("resume text", [], top_n=5) == []

    def test_reranks_and_assigns_scores(self, reranker):
        reranker._fake_model.predict.return_value = np.array([2.0, -2.0])
        candidates = [_candidate("job-1", 0.5), _candidate("job-2", 0.6)]
        result = reranker.rerank("resume text", candidates, top_n=2)
        assert len(result) == 2
        # job-1 has the higher logit (2.0) -> higher sigmoid score -> ranked first
        assert result[0]["jobId"] == "job-1"
        assert result[0]["rank"] == 1
        assert result[1]["rank"] == 2
        assert 0.0 <= result[0]["crossEncoderScore"] <= 1.0

    def test_top_n_truncates_results(self, reranker):
        reranker._fake_model.predict.return_value = np.array([1.0, 2.0, 0.5])
        candidates = [_candidate(f"job-{i}", 0.5) for i in range(3)]
        result = reranker.rerank("resume text", candidates, top_n=1)
        assert len(result) == 1

    def test_prediction_failure_falls_back_to_biencoder_order(self, reranker):
        reranker._fake_model.predict.side_effect = RuntimeError("model crashed")
        candidates = [_candidate("job-1", 0.9), _candidate("job-2", 0.5)]
        result = reranker.rerank("resume text", candidates, top_n=5)
        assert result == candidates[:5]

    def test_pairs_use_field_formatted_text(self, reranker):
        reranker._fake_model.predict.return_value = np.array([1.0])
        reranker.rerank("resume text", [_candidate("job-1", 0.5)], top_n=1)
        pairs = reranker._fake_model.predict.call_args.args[0]
        resume_text, job_text = pairs[0]
        assert "[FULL TEXT]" in resume_text
        assert "[OVERVIEW]" in job_text
        assert "[REQUIREMENTS]" in job_text

    def test_resume_fields_passed_through_to_format(self, reranker):
        reranker._fake_model.predict.return_value = np.array([1.0])
        reranker.rerank(
            "resume text", [_candidate("job-1", 0.5)], top_n=1,
            resume_fields={"resume_skills": "Python, SQL"},
        )
        pairs = reranker._fake_model.predict.call_args.args[0]
        resume_text, _ = pairs[0]
        assert "[SKILLS]\nPython, SQL" in resume_text
        assert "[EDUCATION: MISSING]" in resume_text


class TestFormatResumeText:
    def test_no_structured_fields_marks_all_missing(self, reranker):
        text = reranker._format_resume_text("Full raw resume", None)
        assert "[FULL TEXT]\nFull raw resume" in text
        assert "[SUMMARY: MISSING]" in text
        assert "[EXPERIENCE: MISSING]" in text
        assert "[SKILLS: MISSING]" in text
        assert "[EDUCATION: MISSING]" in text

    def test_structured_fields_included(self, reranker):
        text = reranker._format_resume_text(
            "Full raw resume",
            {"resume_summary": "Backend dev", "resume_skills": "Python"},
        )
        assert "[SUMMARY]\nBackend dev" in text
        assert "[SKILLS]\nPython" in text
        assert "[EXPERIENCE: MISSING]" in text


class TestFormatJobText:
    def test_all_structured_fields_included(self, reranker):
        text = reranker._format_job_text(_candidate("job-1", 0.5))
        assert "[OVERVIEW]\nGreat role" in text
        assert "[REQUIREMENTS]\nPython required" in text
        assert "[RESPONSIBILITIES]\nShip features" in text
        assert "[PREFERRED]\nRemote work" in text
        assert "Job Title: Backend Engineer" in text  # inside job_description_text
        assert "Company: Acme" in text

    def test_missing_fields_marked(self, reranker):
        text = reranker._format_job_text({"title": "Only Title"})
        assert "[OVERVIEW: MISSING]" in text
        assert "[REQUIREMENTS: MISSING]" in text
        assert "[RESPONSIBILITIES: MISSING]" in text
        assert "[PREFERRED: MISSING]" in text
        assert "Job Title: Only Title" in text

    def test_falls_back_to_short_description_when_no_full_description(self, reranker):
        text = reranker._format_job_text({"title": "X", "shortDescription": "Short version"})
        assert "Short version" in text


class TestGetModelInfo:
    def test_get_model_info(self, reranker):
        info = reranker.get_model_info()
        assert info["loaded"] is True
        assert info["type"] == "cross-encoder"
