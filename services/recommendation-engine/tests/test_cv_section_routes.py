"""
Tests for app/api/cv_section_routes.py — the internal /internal/cv/extract-sections
endpoint. The extractor is mocked via get_cv_section_extractor so no Ollama call
is made; the endpoint's disabled/success/failure contract is what's under test.
"""

from unittest.mock import MagicMock


class TestExtractSectionsEndpoint:
    def test_disabled_extractor_returns_not_extracted(self, client, monkeypatch):
        monkeypatch.setattr(
            "app.api.cv_section_routes.get_cv_section_extractor", lambda: None
        )
        resp = client.post("/internal/cv/extract-sections", json={"text": "some resume"})
        assert resp.status_code == 200
        body = resp.json()
        assert body["extracted"] is False
        assert body["summary"] == ""

    def test_blank_text_returns_not_extracted(self, client, monkeypatch):
        extractor = MagicMock()
        monkeypatch.setattr(
            "app.api.cv_section_routes.get_cv_section_extractor", lambda: extractor
        )
        resp = client.post("/internal/cv/extract-sections", json={"text": "   "})
        assert resp.status_code == 200
        assert resp.json()["extracted"] is False
        extractor.extract_resume_sections.assert_not_called()

    def test_success_returns_sections(self, client, monkeypatch):
        extractor = MagicMock()
        extractor.extract_resume_sections.return_value = {
            "summary": "S", "experience": "E", "skills": "Py", "education": "BSc",
        }
        monkeypatch.setattr(
            "app.api.cv_section_routes.get_cv_section_extractor", lambda: extractor
        )
        resp = client.post("/internal/cv/extract-sections", json={"text": "resume text"})
        assert resp.status_code == 200
        body = resp.json()
        assert body["extracted"] is True
        assert body["experience"] == "E"

    def test_all_empty_result_reports_not_extracted(self, client, monkeypatch):
        extractor = MagicMock()
        extractor.extract_resume_sections.return_value = {
            "summary": "", "experience": "", "skills": "", "education": "",
        }
        monkeypatch.setattr(
            "app.api.cv_section_routes.get_cv_section_extractor", lambda: extractor
        )
        resp = client.post("/internal/cv/extract-sections", json={"text": "resume text"})
        assert resp.status_code == 200
        assert resp.json()["extracted"] is False

    def test_extractor_raises_returns_not_extracted_not_500(self, client, monkeypatch):
        extractor = MagicMock()
        extractor.extract_resume_sections.side_effect = RuntimeError("boom")
        monkeypatch.setattr(
            "app.api.cv_section_routes.get_cv_section_extractor", lambda: extractor
        )
        resp = client.post("/internal/cv/extract-sections", json={"text": "resume text"})
        assert resp.status_code == 200
        assert resp.json()["extracted"] is False
