"""
Tests for app/services/cv_refer_sync.py (sync_cv_refer_from_services).

httpx.AsyncClient is patched at the module boundary; asyncio.sleep patched to
avoid real retry delays. Uses a real CvReferStore (in-memory, no I/O).
"""

from unittest.mock import AsyncMock, MagicMock

import httpx
import pytest

from app.services.cv_refer_sync import sync_cv_refer_from_services
from app.services.cv_refer_store import CvReferStore


class _Settings:
    APPLICATION_SERVICE_URL = "http://application-service"
    CV_SERVICE_URL = "http://cv-service"
    INTERNAL_SERVICE_TIMEOUT = 5


def _mock_async_client(get_response=None, get_side_effect=None, post_response=None):
    mock_client = MagicMock()
    if get_side_effect is not None:
        mock_client.get = AsyncMock(side_effect=get_side_effect)
    else:
        mock_client.get = AsyncMock(return_value=get_response)
    if post_response is not None:
        mock_client.post = AsyncMock(return_value=post_response)
    mock_client.__aenter__ = AsyncMock(return_value=mock_client)
    mock_client.__aexit__ = AsyncMock(return_value=False)
    return mock_client


def _resp(body, status_code=200, raise_exc=None):
    resp = MagicMock()
    resp.status_code = status_code
    resp.json.return_value = body
    if raise_exc:
        resp.raise_for_status.side_effect = raise_exc
    else:
        resp.raise_for_status.return_value = None
    return resp


class TestSyncCvReferFromServices:
    async def test_empty_pool_leaves_store_empty(self, monkeypatch):
        store = CvReferStore()
        client = _mock_async_client(get_response=_resp([]))
        monkeypatch.setattr("httpx.AsyncClient", lambda **kw: client)

        await sync_cv_refer_from_services(store, _Settings())
        assert store.get_stats()["total_jobs_tracked"] == 0

    async def test_populates_applicant_pool_without_snapshots(self, monkeypatch):
        store = CvReferStore()
        pool = [{"jobId": "job-1", "applicants": [{"username": "alice"}]}]
        client = _mock_async_client(get_response=_resp(pool))
        monkeypatch.setattr("httpx.AsyncClient", lambda **kw: client)

        await sync_cv_refer_from_services(store, _Settings())
        assert "alice" in store.get_applicant_usernames("job-1")
        assert store.get_cv_data("job-1", "alice") is None

    async def test_fetches_cv_snapshots_when_ids_present(self, monkeypatch):
        store = CvReferStore()
        pool = [
            {
                "jobId": "job-1",
                "applicants": [{"username": "alice", "cvSnapshotId": "snap-1"}],
            }
        ]
        pool_client = _mock_async_client(get_response=_resp(pool))
        monkeypatch.setattr("httpx.AsyncClient", lambda **kw: pool_client)
        pool_client.post = AsyncMock(
            return_value=_resp([{"cvId": "snap-1", "summary": "Backend dev"}])
        )

        await sync_cv_refer_from_services(store, _Settings())
        assert store.get_cv_data("job-1", "alice")["summary"] == "Backend dev"

    async def test_entry_missing_job_id_is_skipped(self, monkeypatch):
        store = CvReferStore()
        pool = [{"applicants": [{"username": "alice"}]}]
        client = _mock_async_client(get_response=_resp(pool))
        monkeypatch.setattr("httpx.AsyncClient", lambda **kw: client)

        await sync_cv_refer_from_services(store, _Settings())
        assert store.get_stats()["total_jobs_tracked"] == 0

    async def test_applicant_without_username_is_skipped(self, monkeypatch):
        store = CvReferStore()
        pool = [{"jobId": "job-1", "applicants": [{}]}]
        client = _mock_async_client(get_response=_resp(pool))
        monkeypatch.setattr("httpx.AsyncClient", lambda **kw: client)

        await sync_cv_refer_from_services(store, _Settings())
        assert store.get_applicant_usernames("job-1") == []

    async def test_http_status_error_on_pool_fetch_returns_gracefully(self, monkeypatch):
        store = CvReferStore()
        error_resp = MagicMock(status_code=500, text="error")
        client = _mock_async_client(
            get_response=_resp(
                {}, raise_exc=httpx.HTTPStatusError("err", request=MagicMock(), response=error_resp)
            )
        )
        monkeypatch.setattr("httpx.AsyncClient", lambda **kw: client)

        await sync_cv_refer_from_services(store, _Settings())  # must not raise
        assert store.get_stats()["total_jobs_tracked"] == 0

    async def test_connect_error_retries_then_gives_up(self, monkeypatch):
        store = CvReferStore()

        def _raise(*a, **kw):
            raise httpx.ConnectError("unreachable")

        client = _mock_async_client(get_side_effect=_raise)
        monkeypatch.setattr("httpx.AsyncClient", lambda **kw: client)
        monkeypatch.setattr("app.services.cv_refer_sync._MAX_RETRIES", 2)
        monkeypatch.setattr("app.services.cv_refer_sync.asyncio.sleep", AsyncMock())

        await sync_cv_refer_from_services(store, _Settings())  # must not raise

    async def test_cv_service_url_not_configured_skips_batch_fetch(self, monkeypatch):
        store = CvReferStore()
        pool = [
            {"jobId": "job-1", "applicants": [{"username": "alice", "cvSnapshotId": "snap-1"}]}
        ]
        client = _mock_async_client(get_response=_resp(pool))
        monkeypatch.setattr("httpx.AsyncClient", lambda **kw: client)

        class _SettingsNoCvService:
            APPLICATION_SERVICE_URL = "http://application-service"
            CV_SERVICE_URL = None
            INTERNAL_SERVICE_TIMEOUT = 5

        await sync_cv_refer_from_services(store, _SettingsNoCvService())
        assert store.get_cv_data("job-1", "alice") is None

    async def test_batch_fetch_failure_is_non_fatal(self, monkeypatch):
        store = CvReferStore()
        pool = [
            {"jobId": "job-1", "applicants": [{"username": "alice", "cvSnapshotId": "snap-1"}]}
        ]
        client = _mock_async_client(get_response=_resp(pool))
        client.post = AsyncMock(side_effect=RuntimeError("cv-service down"))
        monkeypatch.setattr("httpx.AsyncClient", lambda **kw: client)

        await sync_cv_refer_from_services(store, _Settings())  # must not raise
        assert store.get_cv_data("job-1", "alice") is None

    async def test_snapshot_with_unknown_cv_id_is_ignored(self, monkeypatch):
        store = CvReferStore()
        pool = [
            {"jobId": "job-1", "applicants": [{"username": "alice", "cvSnapshotId": "snap-1"}]}
        ]
        client = _mock_async_client(get_response=_resp(pool))
        client.post = AsyncMock(return_value=_resp([{"cvId": "different-id", "summary": "x"}]))
        monkeypatch.setattr("httpx.AsyncClient", lambda **kw: client)

        await sync_cv_refer_from_services(store, _Settings())
        assert store.get_cv_data("job-1", "alice") is None
