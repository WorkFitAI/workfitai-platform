"""
Tests for app/api/cv_refer_internal_routes.py — the internal snapshot-push
endpoint used by application-service's CV snapshot reconciliation job.
"""


def _payload(**overrides):
    payload = {
        "job_id": "job-1",
        "username": "alice",
        "summary": "Backend engineer",
        "experience": "5 years",
        "skills": "Python, FastAPI",
        "education": "BSc CS",
    }
    payload.update(overrides)
    return payload


class TestPushCvSnapshot:
    def test_success_stores_snapshot_in_store(self, client, app_state):
        resp = client.post("/internal/cv-refer/snapshot", json=_payload())
        assert resp.status_code == 200
        assert resp.json() == {"status": "ok"}
        store = app_state["cv_refer_store"]
        assert "alice" in store.get_applicant_usernames("job-1")
        assert store.get_cv_data("job-1", "alice")["summary"] == "Backend engineer"

    def test_empty_snapshot_skips_embedding_precompute(self, client, app_state):
        payload = _payload(summary="", experience="", skills="", education="")
        resp = client.post("/internal/cv-refer/snapshot", json=payload)
        assert resp.status_code == 200
        assert not app_state["cv_ranking_pipeline"].encode_resume.called

    def test_precomputes_embedding_when_pipeline_ready(self, client, app_state, monkeypatch):
        import numpy as np

        app_state["cv_ranking_pipeline"].encode_resume.return_value = np.zeros(4, dtype="float32")
        resp = client.post("/internal/cv-refer/snapshot", json=_payload())
        assert resp.status_code == 200
        assert app_state["cv_ranking_pipeline"].encode_resume.called
        embedding = app_state["cv_refer_store"].get_cv_embedding("job-1", "alice")
        assert embedding is not None

    def test_embedding_precompute_failure_is_non_fatal(self, client, app_state):
        app_state["cv_ranking_pipeline"].encode_resume.side_effect = RuntimeError("model error")
        resp = client.post("/internal/cv-refer/snapshot", json=_payload())
        assert resp.status_code == 200
        assert resp.json() == {"status": "ok"}

    def test_pipeline_not_ready_skips_embedding(self, client, app_state):
        app_state["cv_ranking_pipeline"].is_ready = False
        resp = client.post("/internal/cv-refer/snapshot", json=_payload())
        assert resp.status_code == 200
        assert not app_state["cv_ranking_pipeline"].encode_resume.called

    def test_marks_ranking_dirty_and_triggers_refresh_when_cache_enabled(
        self, client, app_state, monkeypatch
    ):
        from app.config import get_settings

        monkeypatch.setattr(get_settings(), "CV_RANKING_CACHE_ENABLED", True)
        resp = client.post("/internal/cv-refer/snapshot", json=_payload())
        assert resp.status_code == 200
        assert app_state["cv_ranking_refresher"].trigger_immediate.called

    def test_dirty_marking_failure_is_non_fatal(self, client, app_state, monkeypatch):
        from app.config import get_settings

        monkeypatch.setattr(get_settings(), "CV_RANKING_CACHE_ENABLED", True)
        app_state["cv_ranking_refresher"].trigger_immediate.side_effect = RuntimeError("boom")
        resp = client.post("/internal/cv-refer/snapshot", json=_payload())
        assert resp.status_code == 200
        assert resp.json() == {"status": "ok"}

    def test_validation_error_missing_required_fields(self, client):
        resp = client.post("/internal/cv-refer/snapshot", json={"summary": "no ids"})
        assert resp.status_code == 422
