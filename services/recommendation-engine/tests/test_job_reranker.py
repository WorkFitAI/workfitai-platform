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
        "description": "Build APIs", "company": "Acme", "location": "Remote",
        "experienceLevel": "SENIOR", "skills": ["Python", "SQL"], "salary": "100k",
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

    def test_format_job_text_includes_all_present_fields(self, reranker):
        text = reranker._format_job_text(_candidate("job-1", 0.5))
        assert "Job Title: Backend Engineer" in text
        assert "Description: Build APIs" in text
        assert "Company: Acme" in text
        assert "Location: Remote" in text
        assert "Experience: SENIOR" in text
        assert "Required Skills: Python, SQL" in text
        assert "Salary: 100k" in text

    def test_format_job_text_handles_missing_fields(self, reranker):
        text = reranker._format_job_text({"title": "Only Title"})
        assert text == "Job Title: Only Title"

    def test_get_model_info(self, reranker):
        info = reranker.get_model_info()
        assert info["loaded"] is True
        assert info["type"] == "cross-encoder"
