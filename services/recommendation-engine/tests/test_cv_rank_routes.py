"""
Tests for app/api/cv_rank_routes.py — /rank and /rank-by-job/{job_id}.

rank_resumes/build_cv_rank_request/fetch_job_data are patched at the point of
use (imported names inside app.api.cv_rank_routes) since the real pipeline
requires trained model weights we don't want to load in unit tests.
"""

import asyncio

import pytest


def _cv_rank_request_payload():
    return {
        "job": {
            "job_index": 1,
            "jd_overview": "Backend role",
            "jd_requirements": "Python, FastAPI",
            "jd_responsibilities": "Build APIs",
        },
        "resumes": [
            {
                "resume_index": 0,
                "resume_summary": "Experienced backend dev",
                "resume_experience": "5 years Python",
                "resume_skills": "Python, FastAPI",
            }
        ],
    }


def _fake_rank_result(job_index=1, ranked=None):
    ranked = ranked if ranked is not None else [
        {
            "resume_index": 0,
            "score": 82.0,
            "similarity_score": 80.0,
            "cross_score": 84.0,
            "label": "Good Fit",
            "explanation": "Strong match.",
        }
    ]
    return {
        "job_index": job_index,
        "job_overview": "Backend role",
        "total_candidates": 1,
        "processing_time_ms": 12.3,
        "ranked_resumes": ranked,
    }


class TestRankEndpoint:
    def test_success(self, client, app_state, monkeypatch):
        monkeypatch.setattr(
            "app.api.cv_rank_routes.rank_resumes",
            lambda pipeline, request, *a, **kw: _fake_rank_result(),
        )
        resp = client.post("/api/v1/cv-ranking/rank", json=_cv_rank_request_payload())
        assert resp.status_code == 200
        body = resp.json()
        assert body["total_candidates"] == 1
        assert body["ranked_resumes"][0]["label"] == "Good Fit"

    def test_empty_ranked_resumes(self, client, app_state, monkeypatch):
        monkeypatch.setattr(
            "app.api.cv_rank_routes.rank_resumes",
            lambda pipeline, request, *a, **kw: _fake_rank_result(ranked=[]),
        )
        resp = client.post("/api/v1/cv-ranking/rank", json=_cv_rank_request_payload())
        assert resp.status_code == 200
        assert resp.json()["ranked_resumes"] == []

    def test_validation_error_no_resumes(self, client):
        payload = _cv_rank_request_payload()
        payload["resumes"] = []
        resp = client.post("/api/v1/cv-ranking/rank", json=payload)
        assert resp.status_code == 422

    def test_pipeline_not_available_returns_503(self, client, app_state):
        app_state["cv_ranking_pipeline"] = None
        resp = client.post("/api/v1/cv-ranking/rank", json=_cv_rank_request_payload())
        assert resp.status_code == 503

    def test_pipeline_not_ready_returns_503(self, client, app_state):
        app_state["cv_ranking_pipeline"].is_ready = False
        resp = client.post("/api/v1/cv-ranking/rank", json=_cv_rank_request_payload())
        assert resp.status_code == 503

    def test_disabled_by_admin_toggle_returns_503(self, client, app_state):
        app_state["feature_toggle_store"].set("cv-referral", False)
        resp = client.post("/api/v1/cv-ranking/rank", json=_cv_rank_request_payload())
        assert resp.status_code == 503

    def test_runtime_error_returns_503(self, client, app_state, monkeypatch):
        def _raise(*a, **kw):
            raise RuntimeError("pipeline not ready")

        monkeypatch.setattr("app.api.cv_rank_routes.rank_resumes", _raise)
        resp = client.post("/api/v1/cv-ranking/rank", json=_cv_rank_request_payload())
        assert resp.status_code == 503

    def test_unexpected_error_returns_500(self, client, app_state, monkeypatch):
        def _raise(*a, **kw):
            raise ValueError("unexpected")

        monkeypatch.setattr("app.api.cv_rank_routes.rank_resumes", _raise)
        resp = client.post("/api/v1/cv-ranking/rank", json=_cv_rank_request_payload())
        assert resp.status_code == 500


class TestRankByJob:
    def test_404_when_no_applicants(self, client, app_state):
        resp = client.post("/api/v1/cv-ranking/rank-by-job/job-none")
        assert resp.status_code == 404

    def test_pipeline_not_ready_returns_503(self, client, app_state):
        app_state["cv_ranking_pipeline"].is_ready = False
        resp = client.post("/api/v1/cv-ranking/rank-by-job/job-1")
        assert resp.status_code == 503

    def test_disabled_by_admin_toggle_returns_503(self, client, app_state):
        app_state["feature_toggle_store"].set("cv-referral", False)
        resp = client.post("/api/v1/cv-ranking/rank-by-job/job-1")
        assert resp.status_code == 503

    def test_cache_hit_returns_200_immediately(self, client, app_state, monkeypatch):
        from app.config import get_settings
        from app.services.cv_ranking_cache import get_cv_ranking_cache

        monkeypatch.setattr(get_settings(), "CV_RANKING_CACHE_ENABLED", True)
        app_state["cv_refer_store"].add_applicant("job-1", "alice")

        cache = get_cv_ranking_cache()
        cache.try_start_compute("job-1")
        cache.finish_compute(
            "job-1",
            {
                "job_overview": "Backend role",
                "total_candidates": 1,
                "ranked_applicants": [
                    {
                        "username": "alice",
                        "score": 90.0,
                        "similarity_score": 88.0,
                        "cross_score": 91.0,
                        "label": "Good Fit",
                        "explanation": "Great match.",
                    }
                ],
            },
        )
        resp = client.post("/api/v1/cv-ranking/rank-by-job/job-1")
        assert resp.status_code == 200
        body = resp.json()
        assert body["ranked_count"] == 1
        assert body["ranked_applicants"][0]["username"] == "alice"

    def test_cache_miss_returns_202_computing(self, client, app_state, monkeypatch):
        from app.config import get_settings

        monkeypatch.setattr(get_settings(), "CV_RANKING_CACHE_ENABLED", True)
        app_state["cv_refer_store"].add_applicant("job-2", "bob")

        # Prevent the real background task from running (it would need job-service HTTP).
        monkeypatch.setattr(
            "app.api.cv_rank_routes.asyncio.create_task", lambda coro: coro.close()
        )
        resp = client.post("/api/v1/cv-ranking/rank-by-job/job-2")
        assert resp.status_code == 202
        body = resp.json()
        assert body["status"] == "computing"
        assert "retry_after_seconds" in body

    def test_cache_disabled_synchronous_success(self, client, app_state, monkeypatch):
        from app.config import get_settings

        monkeypatch.setattr(get_settings(), "CV_RANKING_CACHE_ENABLED", False)
        app_state["cv_refer_store"].add_applicant("job-3", "carol")
        app_state["cv_refer_store"].set_cv_snapshot(
            "job-3", "carol", {"summary": "s", "experience": "e", "skills": "sk", "education": "ed"}
        )

        async def _fake_fetch_job_data(url, job_id, timeout):
            return {"shortDescription": "Backend role"}

        monkeypatch.setattr("app.api.cv_rank_routes.fetch_job_data", _fake_fetch_job_data)
        monkeypatch.setattr(
            "app.api.cv_rank_routes.rank_resumes",
            lambda pipeline, request, *a, **kw: _fake_rank_result(job_index=0),
        )
        resp = client.post("/api/v1/cv-ranking/rank-by-job/job-3")
        assert resp.status_code == 200
        body = resp.json()
        assert body["ranked_count"] == 1

    def test_cache_disabled_job_service_unreachable_returns_503(self, client, app_state, monkeypatch):
        from app.config import get_settings

        monkeypatch.setattr(get_settings(), "CV_RANKING_CACHE_ENABLED", False)
        app_state["cv_refer_store"].add_applicant("job-4", "dave")

        async def _fake_fetch_job_data(url, job_id, timeout):
            return None

        monkeypatch.setattr("app.api.cv_rank_routes.fetch_job_data", _fake_fetch_job_data)
        resp = client.post("/api/v1/cv-ranking/rank-by-job/job-4")
        assert resp.status_code == 503


class TestBackgroundRankByJob:
    """Directly exercise _background_rank_by_job — the async task kicked off on cache miss."""

    async def test_background_compute_success_calls_finish_compute(self, monkeypatch):
        from app.api.cv_rank_routes import _background_rank_by_job
        from app.services.cv_refer_store import CvReferStore

        store = CvReferStore()
        store.add_applicant("job-5", "erin")
        store.set_cv_snapshot("job-5", "erin", {"summary": "s", "experience": "e", "skills": "sk", "education": "ed"})

        class _Settings:
            JOB_SERVICE_URL = "http://job-service"
            JOB_SERVICE_TIMEOUT = 5

        async def _fake_fetch_job_data(url, job_id, timeout):
            return {"shortDescription": "Backend role"}

        monkeypatch.setattr("app.api.cv_rank_routes.fetch_job_data", _fake_fetch_job_data)
        monkeypatch.setattr(
            "app.api.cv_rank_routes.rank_resumes",
            lambda pipeline, request, *a, **kw: _fake_rank_result(job_index=0),
        )

        finished = {}

        class _FakeCache:
            def finish_compute(self, job_id, result):
                finished["job_id"] = job_id
                finished["result"] = result

        await _background_rank_by_job(
            "job-5", store, _Settings(), pipeline=object(), cache=_FakeCache(), options=None
        )
        assert finished["job_id"] == "job-5"
        assert finished["result"]["ranked_applicants"][0].username == "erin"

    async def test_background_compute_job_fetch_failure_finishes_with_none(self, monkeypatch):
        from app.api.cv_rank_routes import _background_rank_by_job
        from app.services.cv_refer_store import CvReferStore

        store = CvReferStore()

        class _Settings:
            JOB_SERVICE_URL = "http://job-service"
            JOB_SERVICE_TIMEOUT = 5

        async def _fake_fetch_job_data(url, job_id, timeout):
            return None

        monkeypatch.setattr("app.api.cv_rank_routes.fetch_job_data", _fake_fetch_job_data)

        finished = {}

        class _FakeCache:
            def finish_compute(self, job_id, result):
                finished["job_id"] = job_id
                finished["result"] = result

        await _background_rank_by_job(
            "job-6", store, _Settings(), pipeline=object(), cache=_FakeCache(), options=None
        )
        assert finished["job_id"] == "job-6"
        assert finished["result"] is None
