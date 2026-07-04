"""
Tests for app/services/cv_rank_assembly.py (fetch_job_data, build_cv_rank_request).

httpx.AsyncClient is patched at the boundary for fetch_job_data; CvReferStore is
a real in-memory instance for build_cv_rank_request.
"""

from unittest.mock import AsyncMock, MagicMock

import httpx
import pytest

from app.services.cv_rank_assembly import build_cv_rank_request, fetch_job_data
from app.services.cv_refer_store import CvReferStore


def _mock_async_client(response=None, raise_exc=None):
    mock_client = MagicMock()
    if raise_exc is not None:
        mock_client.get = AsyncMock(side_effect=raise_exc)
    else:
        mock_client.get = AsyncMock(return_value=response)
    mock_client.__aenter__ = AsyncMock(return_value=mock_client)
    mock_client.__aexit__ = AsyncMock(return_value=False)
    return mock_client


def _response(body: dict):
    resp = MagicMock()
    resp.raise_for_status.return_value = None
    resp.json.return_value = body
    return resp


class TestFetchJobData:
    async def test_success_returns_data_field(self, monkeypatch):
        client = _mock_async_client(_response({"data": {"title": "Backend Engineer"}}))
        monkeypatch.setattr("httpx.AsyncClient", lambda **kw: client)
        result = await fetch_job_data("http://job-service", "job-1", 5)
        assert result == {"title": "Backend Engineer"}

    async def test_success_falls_back_to_whole_body_when_no_data_field(self, monkeypatch):
        client = _mock_async_client(_response({"title": "Backend Engineer"}))
        monkeypatch.setattr("httpx.AsyncClient", lambda **kw: client)
        result = await fetch_job_data("http://job-service", "job-1", 5)
        assert result == {"title": "Backend Engineer"}

    async def test_failure_returns_none(self, monkeypatch):
        client = _mock_async_client(raise_exc=httpx.ConnectError("unreachable"))
        monkeypatch.setattr("httpx.AsyncClient", lambda **kw: client)
        result = await fetch_job_data("http://job-service", "job-1", 5)
        assert result is None


class TestBuildCvRankRequest:
    def test_no_applicants_returns_reason(self):
        store = CvReferStore()
        request, idx_map, reason, embeddings = build_cv_rank_request(
            "job-1", store, {"title": "Backend Engineer"}
        )
        assert request is None
        assert idx_map is None
        assert "No active applicants" in reason
        assert embeddings == {}

    def test_no_cv_data_synced_returns_reason(self):
        store = CvReferStore()
        store.add_applicant("job-1", "alice")
        request, idx_map, reason, embeddings = build_cv_rank_request(
            "job-1", store, {"title": "Backend Engineer"}
        )
        assert request is None
        assert "CV data has not been synced" in reason

    def test_success_builds_request_with_embeddings(self):
        store = CvReferStore()
        store.add_applicant("job-1", "alice")
        store.set_cv_snapshot(
            "job-1", "alice",
            {"summary": "s", "experience": "e", "skills": "sk", "education": "ed"},
        )
        store.set_cv_embedding("job-1", "alice", [0.1, 0.2])

        request, idx_map, reason, embeddings = build_cv_rank_request(
            "job-1",
            store,
            {
                "shortDescription": "Backend role",
                "requirements": "Python",
                "responsibilities": "Build APIs",
                "benefits": "Remote",
                "description": "Full JD text",
            },
        )
        assert request is not None
        assert reason == ""
        assert idx_map == {0: "alice"}
        assert embeddings == {0: [0.1, 0.2]}
        assert request.job.jd_overview == "Backend role"
        assert len(request.resumes) == 1
        assert request.resumes[0].resume_summary == "s"

    def test_partial_cv_data_missing_logs_and_skips(self):
        store = CvReferStore()
        store.add_applicant("job-1", "alice")
        store.add_applicant("job-1", "bob")
        store.set_cv_snapshot("job-1", "alice", {"summary": "s"})
        # bob has no CV data synced yet

        request, idx_map, reason, embeddings = build_cv_rank_request(
            "job-1", store, {"title": "Backend Engineer"}
        )
        assert request is not None
        assert len(request.resumes) == 1
        assert list(idx_map.values()) == ["alice"]

    def test_job_data_uses_title_fallback_when_no_short_description(self):
        store = CvReferStore()
        store.add_applicant("job-1", "alice")
        store.set_cv_snapshot("job-1", "alice", {"summary": "s"})

        request, _, _, _ = build_cv_rank_request(
            "job-1", store, {"title": "Fallback Title"}
        )
        assert request.job.jd_overview == "Fallback Title"

    def test_missing_embedding_defaults_to_absent_entry(self):
        store = CvReferStore()
        store.add_applicant("job-1", "alice")
        store.set_cv_snapshot("job-1", "alice", {"summary": "s"})
        # no embedding cached for alice

        _, _, _, embeddings = build_cv_rank_request("job-1", store, {"title": "T"})
        assert embeddings == {}
