"""
Tests for app/services/ai_consent_client.py.

httpx.AsyncClient is patched at the module boundary — no real HTTP call.
"""

from unittest.mock import AsyncMock, MagicMock

import httpx
import pytest

from app.services.ai_consent_client import fetch_ai_job_recommendation_consent


def _mock_async_client(response=None, raise_exc=None):
    mock_client = MagicMock()
    if raise_exc is not None:
        mock_client.get = AsyncMock(side_effect=raise_exc)
    else:
        mock_client.get = AsyncMock(return_value=response)
    mock_client.__aenter__ = AsyncMock(return_value=mock_client)
    mock_client.__aexit__ = AsyncMock(return_value=False)
    return mock_client


def _response(body: dict, status_code: int = 200):
    resp = MagicMock()
    resp.status_code = status_code
    resp.json.return_value = body
    if status_code >= 400:
        resp.raise_for_status.side_effect = httpx.HTTPStatusError(
            "error", request=MagicMock(), response=resp
        )
    else:
        resp.raise_for_status.return_value = None
    return resp


class TestFetchAiJobRecommendationConsent:
    @pytest.mark.parametrize("enabled_value", [True, None])
    async def test_consent_granted_or_unspecified(self, monkeypatch, enabled_value):
        client = _mock_async_client(_response({"aiJobRecommendationEnabled": enabled_value}))
        monkeypatch.setattr("httpx.AsyncClient", lambda **kw: client)
        result = await fetch_ai_job_recommendation_consent("http://user-service", "alice", 5)
        assert result is True

    async def test_consent_explicitly_denied(self, monkeypatch):
        client = _mock_async_client(_response({"aiJobRecommendationEnabled": False}))
        monkeypatch.setattr("httpx.AsyncClient", lambda **kw: client)
        result = await fetch_ai_job_recommendation_consent("http://user-service", "alice", 5)
        assert result is False

    async def test_http_error_fails_closed(self, monkeypatch):
        client = _mock_async_client(raise_exc=httpx.ConnectError("unreachable"))
        monkeypatch.setattr("httpx.AsyncClient", lambda **kw: client)
        result = await fetch_ai_job_recommendation_consent("http://user-service", "alice", 5)
        assert result is False

    async def test_non_200_status_fails_closed(self, monkeypatch):
        client = _mock_async_client(_response({}, status_code=500))
        monkeypatch.setattr("httpx.AsyncClient", lambda **kw: client)
        result = await fetch_ai_job_recommendation_consent("http://user-service", "alice", 5)
        assert result is False
