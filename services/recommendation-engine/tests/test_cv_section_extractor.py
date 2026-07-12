"""
Tests for app/services/cv_section_extractor.py.

The Ollama Client is mocked at the module boundary — no real network call. The
key-rotation, JSON-parsing, fence-stripping and best-effort-empty-on-failure
decision logic is what's under test.
"""

from unittest.mock import MagicMock

import pytest

from app.services import cv_section_extractor as mod
from app.services.cv_section_extractor import CvSectionExtractor, get_cv_section_extractor


def _chat_returning(content: str):
    """Build a fake ollama.Client whose .chat() returns the given message content."""
    client = MagicMock()
    client.chat.return_value = {"message": {"content": content}}
    return client


class TestExtractResumeSections:
    def test_valid_json_parsed(self, monkeypatch):
        extractor = CvSectionExtractor(api_keys=["k1"])
        client = _chat_returning(
            '{"summary": "S", "experience": "E", "skills": "Py", "education": "BSc"}'
        )
        monkeypatch.setattr(extractor, "_get_client", lambda: client)
        result = extractor.extract_resume_sections("resume text")
        assert result == {"summary": "S", "experience": "E", "skills": "Py", "education": "BSc"}

    def test_markdown_fenced_json_stripped(self, monkeypatch):
        extractor = CvSectionExtractor(api_keys=["k1"])
        client = _chat_returning('```json\n{"summary": "S", "experience": "", "skills": "", "education": ""}\n```')
        monkeypatch.setattr(extractor, "_get_client", lambda: client)
        result = extractor.extract_resume_sections("resume text")
        assert result["summary"] == "S"

    def test_blank_input_returns_empty_without_calling_ollama(self, monkeypatch):
        extractor = CvSectionExtractor(api_keys=["k1"])
        client = _chat_returning("{}")
        monkeypatch.setattr(extractor, "_get_client", lambda: client)
        result = extractor.extract_resume_sections("   ")
        assert result == {"summary": "", "experience": "", "skills": "", "education": ""}
        client.chat.assert_not_called()

    def test_rate_limit_rotates_key_then_succeeds(self, monkeypatch):
        extractor = CvSectionExtractor(api_keys=["k1", "k2"])
        good = _chat_returning('{"summary": "S", "experience": "", "skills": "", "education": ""}')
        bad = MagicMock()
        bad.chat.side_effect = RuntimeError("429 Too Many Requests")

        # First call (key idx 0) -> rate-limited; after rotation (idx 1) -> success.
        def fake_get_client():
            return bad if extractor.current_key_idx == 0 else good

        monkeypatch.setattr(extractor, "_get_client", fake_get_client)
        monkeypatch.setattr(mod.time, "sleep", lambda *_: None)
        result = extractor.extract_resume_sections("resume text")
        assert result["summary"] == "S"
        assert extractor.current_key_idx == 1

    def test_bad_json_on_final_attempt_returns_empty(self, monkeypatch):
        extractor = CvSectionExtractor(api_keys=["k1"])
        client = _chat_returning("not json at all")
        monkeypatch.setattr(extractor, "_get_client", lambda: client)
        monkeypatch.setattr(mod.time, "sleep", lambda *_: None)
        result = extractor.extract_resume_sections("resume text", max_retries=2)
        assert result == {"summary": "", "experience": "", "skills": "", "education": ""}

    def test_long_section_is_char_capped(self, monkeypatch):
        extractor = CvSectionExtractor(api_keys=["k1"])
        long_text = "x" * 5000
        client = _chat_returning(
            '{"summary": "' + long_text + '", "experience": "", "skills": "", "education": ""}'
        )
        monkeypatch.setattr(extractor, "_get_client", lambda: client)
        result = extractor.extract_resume_sections("resume text")
        assert len(result["summary"]) <= mod._MAX_SECTION_CHARS + 3  # +3 for "..."
        assert result["summary"].endswith("...")

    def test_extra_unknown_keys_ignored(self, monkeypatch):
        extractor = CvSectionExtractor(api_keys=["k1"])
        client = _chat_returning('{"summary": "S", "bogus": "X"}')
        monkeypatch.setattr(extractor, "_get_client", lambda: client)
        result = extractor.extract_resume_sections("resume text")
        assert set(result.keys()) == {"summary", "experience", "skills", "education"}
        assert result["summary"] == "S"

    def test_no_keys_raises(self):
        with pytest.raises(ValueError):
            CvSectionExtractor(api_keys=[])


class TestGetCvSectionExtractor:
    def _reset_singleton(self):
        mod._extractor = None
        mod._extractor_initialised = False

    def test_disabled_returns_none(self, monkeypatch):
        self._reset_singleton()
        settings = MagicMock()
        settings.CV_OLLAMA_EXTRACTION_ENABLED = False
        monkeypatch.setattr("app.config.get_settings", lambda: settings)
        assert get_cv_section_extractor() is None
        self._reset_singleton()

    def test_no_keys_returns_none(self, monkeypatch):
        self._reset_singleton()
        settings = MagicMock()
        settings.CV_OLLAMA_EXTRACTION_ENABLED = True
        settings.get_ollama_api_keys.return_value = []
        monkeypatch.setattr("app.config.get_settings", lambda: settings)
        assert get_cv_section_extractor() is None
        self._reset_singleton()

    def test_enabled_with_keys_builds_extractor(self, monkeypatch):
        self._reset_singleton()
        settings = MagicMock()
        settings.CV_OLLAMA_EXTRACTION_ENABLED = True
        settings.get_ollama_api_keys.return_value = ["k1", "k2"]
        settings.OLLAMA_MODEL = "gpt-oss:120b"
        monkeypatch.setattr("app.config.get_settings", lambda: settings)
        extractor = get_cv_section_extractor()
        assert isinstance(extractor, CvSectionExtractor)
        assert extractor.api_keys == ["k1", "k2"]
        self._reset_singleton()
