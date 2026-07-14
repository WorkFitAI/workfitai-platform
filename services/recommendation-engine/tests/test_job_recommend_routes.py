"""
Tests for app/api/job_recommend_routes.py — by-resume, by-profile, similar-jobs,
search, and admin/sync endpoints.

Uses the `client` fixture (conftest.py) — faiss_manager/embedding/reranker are
mocked; feature_toggle_store/cv_refer_store are real in-memory instances.
"""

import base64

import numpy as np
import pytest


def _faiss_result(job_id="job-1", score=0.9, **overrides):
    result = {
        "jobId": job_id,
        "score": score,
        "title": "Backend Engineer",
        "company": {"companyName": "Acme"},
        "location": "Remote",
        "salary": "100k",
        "experienceLevel": "SENIOR",
        "jobType": "FULL_TIME",
        "skills": ["Python"],
    }
    result.update(overrides)
    return result


class TestByResume:
    def test_success_returns_recommendations(self, client, app_state):
        app_state["faiss_manager"].search.return_value = [_faiss_result()]
        payload = {
            "resumeFile": base64.b64encode(b"%PDF-1.4 fake").decode(),
            "topK": 5,
        }
        resp = client.post("/api/v1/recommendations/by-resume", json=payload)
        assert resp.status_code == 200
        body = resp.json()
        assert body["success"] is True
        assert body["data"]["totalResults"] == 1
        assert body["data"]["recommendations"][0]["jobId"] == "job-1"

    def test_empty_result_when_faiss_returns_nothing(self, client, app_state):
        app_state["faiss_manager"].search.return_value = []
        payload = {"resumeFile": base64.b64encode(b"%PDF-1.4 fake").decode(), "topK": 5}
        resp = client.post("/api/v1/recommendations/by-resume", json=payload)
        assert resp.status_code == 200
        assert resp.json()["data"]["totalResults"] == 0

    def test_invalid_base64_returns_400(self, client):
        payload = {"resumeFile": "not-valid-base64!!!", "topK": 5}
        resp = client.post("/api/v1/recommendations/by-resume", json=payload)
        assert resp.status_code == 400

    def test_resume_parser_returns_none_returns_400(self, client, app_state):
        app_state["resume_parser"].parse_resume.return_value = None
        payload = {"resumeFile": base64.b64encode(b"%PDF-1.4 fake").decode(), "topK": 5}
        resp = client.post("/api/v1/recommendations/by-resume", json=payload)
        assert resp.status_code == 400

    def test_resume_parser_raises_returns_400(self, client, app_state):
        app_state["resume_parser"].parse_resume.side_effect = RuntimeError("boom")
        payload = {"resumeFile": base64.b64encode(b"%PDF-1.4 fake").decode(), "topK": 5}
        resp = client.post("/api/v1/recommendations/by-resume", json=payload)
        assert resp.status_code == 400

    def test_missing_faiss_manager_returns_503(self, client, app_state):
        app_state["faiss_manager"] = None
        payload = {"resumeFile": base64.b64encode(b"%PDF-1.4 fake").decode(), "topK": 5}
        resp = client.post("/api/v1/recommendations/by-resume", json=payload)
        assert resp.status_code == 503

    def test_validation_error_top_k_out_of_range(self, client):
        payload = {"resumeFile": base64.b64encode(b"%PDF-1.4 fake").decode(), "topK": 0}
        resp = client.post("/api/v1/recommendations/by-resume", json=payload)
        assert resp.status_code == 422

    def test_disabled_by_admin_toggle_returns_503(self, client, app_state):
        app_state["feature_toggle_store"].set("job-recommendation", False)
        payload = {"resumeFile": base64.b64encode(b"%PDF-1.4 fake").decode(), "topK": 5}
        resp = client.post("/api/v1/recommendations/by-resume", json=payload)
        assert resp.status_code == 503

    def test_cache_enabled_path_still_returns_results(self, client, app_state, monkeypatch):
        from app.config import get_settings

        monkeypatch.setattr(get_settings(), "RECOMMENDATION_CACHE_ENABLED", True)
        app_state["faiss_manager"].search.return_value = [_faiss_result()]
        payload = {"resumeFile": base64.b64encode(b"%PDF-1.4 fake").decode(), "topK": 5}
        resp = client.post("/api/v1/recommendations/by-resume", json=payload)
        assert resp.status_code == 200
        assert resp.json()["data"]["totalResults"] == 1
        # second call should be a cache hit (no assertion on hit/miss, just no error)
        resp2 = client.post("/api/v1/recommendations/by-resume", json=payload)
        assert resp2.status_code == 200


class TestByProfile:
    def test_success_without_reranker(self, client, app_state):
        app_state["reranker"] = None
        app_state["faiss_manager"].search.return_value = [_faiss_result()]
        payload = {"profileText": "Experienced backend engineer", "topK": 5}
        resp = client.post("/api/v1/recommendations/by-profile", json=payload)
        assert resp.status_code == 200
        assert resp.json()["data"]["totalResults"] == 1

    def test_success_with_reranker(self, client, app_state):
        app_state["faiss_manager"].search.return_value = [_faiss_result()]
        payload = {"profileText": "Experienced backend engineer", "topK": 5}
        resp = client.post("/api/v1/recommendations/by-profile", json=payload)
        assert resp.status_code == 200
        assert app_state["reranker"].rerank.called

    def test_reranker_failure_falls_back_to_biencoder(self, client, app_state):
        app_state["faiss_manager"].search.return_value = [_faiss_result()]
        app_state["reranker"].rerank.side_effect = RuntimeError("cross-encoder exploded")
        payload = {"profileText": "Experienced backend engineer", "topK": 5}
        resp = client.post("/api/v1/recommendations/by-profile", json=payload)
        assert resp.status_code == 200
        assert resp.json()["data"]["totalResults"] == 1

    def test_empty_result(self, client, app_state):
        app_state["faiss_manager"].search.return_value = []
        payload = {"profileText": "Experienced backend engineer", "topK": 5}
        resp = client.post("/api/v1/recommendations/by-profile", json=payload)
        assert resp.status_code == 200
        assert resp.json()["data"]["totalResults"] == 0

    def test_validation_error_profile_text_too_short(self, client):
        payload = {"profileText": "hi", "topK": 5}
        resp = client.post("/api/v1/recommendations/by-profile", json=payload)
        assert resp.status_code == 422


class TestByProfileConsentAndService:
    """Admin toggle / candidate-consent gating and service-readiness for the
    by-profile endpoint."""

    def test_missing_service_returns_503(self, client, app_state):
        app_state["model"] = None
        payload = {"profileText": "Experienced backend engineer", "topK": 5}
        resp = client.post("/api/v1/recommendations/by-profile", json=payload)
        assert resp.status_code == 503

    def test_candidate_consent_denied_returns_503(self, client, app_state, monkeypatch):
        app_state["faiss_manager"].search.return_value = [_faiss_result()]

        async def _deny(*args, **kwargs):
            return False

        monkeypatch.setattr(
            "app.services.ai_consent_client.fetch_ai_job_recommendation_consent", _deny
        )
        payload = {
            "profileText": "Experienced backend engineer",
            "topK": 5,
            "candidateUsername": "jane",
        }
        resp = client.post("/api/v1/recommendations/by-profile", json=payload)
        assert resp.status_code == 503

    def test_candidate_consent_granted_returns_200(self, client, app_state, monkeypatch):
        app_state["faiss_manager"].search.return_value = [_faiss_result()]

        async def _allow(*args, **kwargs):
            return True

        monkeypatch.setattr(
            "app.services.ai_consent_client.fetch_ai_job_recommendation_consent", _allow
        )
        payload = {
            "profileText": "Experienced backend engineer",
            "topK": 5,
            "candidateUsername": "jane",
        }
        resp = client.post("/api/v1/recommendations/by-profile", json=payload)
        assert resp.status_code == 200


class TestByProfileMultiField:
    """profileText-only requests are covered by TestByProfile above and must
    stay byte-for-byte unaffected; these tests cover the new structured-field
    path (resumeSummary/Experience/Skills/Education -> transparency payload)."""

    def test_plain_request_leaves_transparency_fields_none(self, client, app_state):
        app_state["reranker"] = None
        app_state["faiss_manager"].search.return_value = [_faiss_result()]
        payload = {"profileText": "Experienced backend engineer", "topK": 5}
        resp = client.post("/api/v1/recommendations/by-profile", json=payload)
        rec = resp.json()["data"]["recommendations"][0]
        assert rec["resumeFieldsPresent"] is None
        assert rec["perFieldSimilarity"] is None
        assert not app_state["model"].encode_resume_fields.called

    def test_structured_field_activates_multi_field_encode(self, client, app_state):
        app_state["reranker"] = None
        app_state["faiss_manager"].search.return_value = [_faiss_result()]
        payload = {
            "profileText": "Experienced backend engineer",
            "resumeSkills": "Python, SQL",
            "topK": 5,
        }
        resp = client.post("/api/v1/recommendations/by-profile", json=payload)
        assert resp.status_code == 200
        assert app_state["model"].encode_resume_fields.called
        assert not app_state["model"].encode_resume.called

    def test_transparency_payload_populated(self, client, app_state):
        app_state["reranker"] = None
        dim = 4
        app_state["model"].encode_resume_fields.return_value = (
            np.array([1.0, 0.0, 0.0, 0.0], dtype=np.float32),
            {
                "resume_text": np.array([1.0, 0.0, 0.0, 0.0], dtype=np.float32),
                "resume_skills": np.array([0.0, 1.0, 0.0, 0.0], dtype=np.float32),
            },
            [True, False, False, True, False],  # resume_text, resume_summary, resume_experience, resume_skills, resume_education
        )
        app_state["faiss_manager"].search.return_value = [_faiss_result()]
        app_state["faiss_manager"].get_job_field_embeddings.return_value = {
            "job_description_text": np.array([1.0, 0.0, 0.0, 0.0], dtype=np.float32),
            "jd_requirements": np.array([0.0, 1.0, 0.0, 0.0], dtype=np.float32),
        }
        payload = {
            "profileText": "Experienced backend engineer",
            "resumeSkills": "Python, SQL",
            "topK": 5,
        }
        resp = client.post("/api/v1/recommendations/by-profile", json=payload)
        rec = resp.json()["data"]["recommendations"][0]

        assert set(rec["resumeFieldsPresent"]) == {"resume_text", "resume_skills"}
        assert set(rec["jobFieldsPresent"]) == {"job_description_text", "jd_requirements"}
        assert rec["fieldsUnavailable"]["resume"] == ["resume_summary", "resume_experience", "resume_education"]
        assert "jd_overview" in rec["fieldsUnavailable"]["job"]
        assert "resume:resume_text" in rec["perFieldSimilarity"]
        assert "job:jd_requirements" in rec["perFieldSimilarity"]
        assert rec["matchReasons"] is not None
        assert "Top contributing fields" in rec["matchReasons"][0]

    def test_no_job_field_data_leaves_similarity_empty_but_no_crash(self, client, app_state):
        app_state["reranker"] = None
        app_state["faiss_manager"].search.return_value = [_faiss_result()]
        app_state["faiss_manager"].get_job_field_embeddings.return_value = {}
        payload = {
            "profileText": "Experienced backend engineer",
            "resumeSkills": "Python, SQL",
            "topK": 5,
        }
        resp = client.post("/api/v1/recommendations/by-profile", json=payload)
        assert resp.status_code == 200
        rec = resp.json()["data"]["recommendations"][0]
        assert rec["perFieldSimilarity"] == {}
        assert rec["jobFieldsPresent"] == []

    def test_transparency_survives_reranking(self, client, app_state):
        """candidates round-trip through model_dump() -> reranker.rerank() ->
        JobRecommendation(**r) -- the new fields must not get dropped."""
        app_state["faiss_manager"].search.return_value = [_faiss_result()]
        app_state["faiss_manager"].get_job_field_embeddings.return_value = {
            "job_description_text": np.array([1.0, 0.0, 0.0, 0.0], dtype=np.float32),
        }
        payload = {
            "profileText": "Experienced backend engineer",
            "resumeSkills": "Python, SQL",
            "topK": 5,
        }
        resp = client.post("/api/v1/recommendations/by-profile", json=payload)
        assert resp.status_code == 200
        rec = resp.json()["data"]["recommendations"][0]
        assert rec["resumeFieldsPresent"] is not None


class TestSimilarJobs:
    def test_success(self, client, app_state):
        app_state["faiss_manager"].get_job_by_id.return_value = {
            "jobId": "job-1", "embedding": [0.1, 0.2], "company": {"companyName": "Acme"},
        }
        app_state["faiss_manager"].search.return_value = [
            _faiss_result(job_id="job-1"), _faiss_result(job_id="job-2", **{"company": {"companyName": "Other"}}),
        ]
        payload = {"jobId": "job-1", "topK": 5}
        resp = client.post("/api/v1/recommendations/similar-jobs", json=payload)
        assert resp.status_code == 200
        body = resp.json()
        # reference job itself is excluded
        assert all(r["jobId"] != "job-1" for r in body["data"]["recommendations"])

    def test_excludes_same_company_when_requested(self, client, app_state):
        app_state["faiss_manager"].get_job_by_id.return_value = {
            "jobId": "job-1", "embedding": [0.1, 0.2], "company": {"companyName": "Acme"},
        }
        app_state["faiss_manager"].search.return_value = [
            _faiss_result(job_id="job-1"),
            _faiss_result(job_id="job-2", company={"companyName": "Acme"}),
            _faiss_result(job_id="job-3", company={"companyName": "Other"}),
        ]
        payload = {"jobId": "job-1", "topK": 5, "excludeSameCompany": True}
        resp = client.post("/api/v1/recommendations/similar-jobs", json=payload)
        assert resp.status_code == 200
        job_ids = [r["jobId"] for r in resp.json()["data"]["recommendations"]]
        assert "job-2" not in job_ids
        assert "job-3" in job_ids

    def test_job_not_found_returns_404(self, client, app_state):
        app_state["faiss_manager"].get_job_by_id.return_value = None
        payload = {"jobId": "missing-job", "topK": 5}
        resp = client.post("/api/v1/recommendations/similar-jobs", json=payload)
        assert resp.status_code == 404

    def test_missing_faiss_manager_returns_503(self, client, app_state):
        app_state["faiss_manager"] = None
        payload = {"jobId": "job-1", "topK": 5}
        resp = client.post("/api/v1/recommendations/similar-jobs", json=payload)
        assert resp.status_code == 503


class TestSemanticSearch:
    def test_success(self, client, app_state):
        app_state["faiss_manager"].search.return_value = [_faiss_result()]
        payload = {"query": "backend engineer", "topK": 5}
        resp = client.post("/api/v1/recommendations/search", json=payload)
        assert resp.status_code == 200
        assert resp.json()["data"]["totalResults"] == 1

    def test_empty_result(self, client, app_state):
        app_state["faiss_manager"].search.return_value = []
        payload = {"query": "backend engineer", "topK": 5}
        resp = client.post("/api/v1/recommendations/search", json=payload)
        assert resp.status_code == 200
        assert resp.json()["data"]["totalResults"] == 0

    def test_validation_error_query_too_short(self, client):
        payload = {"query": "ab", "topK": 5}
        resp = client.post("/api/v1/recommendations/search", json=payload)
        assert resp.status_code == 422

    def test_disabled_by_admin_toggle(self, client, app_state):
        app_state["feature_toggle_store"].set("job-recommendation", False)
        payload = {"query": "backend engineer", "topK": 5}
        resp = client.post("/api/v1/recommendations/search", json=payload)
        assert resp.status_code == 503

    def test_missing_service_returns_503(self, client, app_state):
        app_state["faiss_manager"] = None
        payload = {"query": "backend engineer", "topK": 5}
        resp = client.post("/api/v1/recommendations/search", json=payload)
        assert resp.status_code == 503


class TestAdminSync:
    def test_trigger_sync_success(self, client, app_state, monkeypatch):
        async def _fake_sync(faiss_manager, embedding_generator):
            return 7

        monkeypatch.setattr("app.services.job_sync.sync_jobs_from_service", _fake_sync)
        resp = client.post("/api/v1/recommendations/admin/sync")
        assert resp.status_code == 200
        body = resp.json()
        assert body["success"] is True
        assert body["jobsSynced"] == 7

    def test_missing_service_returns_503(self, client, app_state):
        app_state["faiss_manager"] = None
        resp = client.post("/api/v1/recommendations/admin/sync")
        assert resp.status_code == 503
