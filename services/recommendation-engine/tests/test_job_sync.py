"""
Tests for app/services/job_sync.py (_normalize_job_data, sync_jobs_from_service).

httpx.AsyncClient is patched at the module boundary; asyncio.sleep is patched to
avoid real retry delays.
"""

from unittest.mock import AsyncMock, MagicMock

import httpx
import numpy as np
import pytest

from app.services.field_format import JOB_FIELDS
from app.services.job_sync import _normalize_job_data, sync_jobs_from_service


class TestNormalizeJobData:
    def test_maps_all_fields(self):
        job = {
            "postId": "job-1",
            "title": "Backend Engineer",
            "shortDescription": "Great role",
            "company": {
                "address": "Remote", "companyNo": "co-1", "name": "Acme", "size": "50-200",
            },
            "employmentType": "FULL_TIME",
            "experienceLevel": "SENIOR",
            "salaryMin": 90000,
            "salaryMax": 120000,
            "skillNames": ["Python"],
            "expiresAt": "2027-01-01",
            "createdDate": "2026-01-01",
        }
        normalized = _normalize_job_data(job)
        assert normalized["id"] == "job-1"
        assert normalized["description"] == "Great role"
        assert normalized["location"] == "Remote"
        assert normalized["company"]["companyId"] == "co-1"
        assert normalized["company"]["companyName"] == "Acme"
        assert normalized["skills"] == ["Python"]

    def test_missing_company_defaults_gracefully(self):
        normalized = _normalize_job_data({"postId": "job-1"})
        assert normalized["location"] == ""
        assert normalized["company"]["companyName"] == ""
        assert normalized["skills"] == []


def _mock_async_client(get_side_effect):
    mock_client = MagicMock()
    mock_client.get = AsyncMock(side_effect=get_side_effect)
    mock_client.__aenter__ = AsyncMock(return_value=mock_client)
    mock_client.__aexit__ = AsyncMock(return_value=False)
    return mock_client


def _page_response(jobs, page=0, pages=1):
    resp = MagicMock()
    resp.raise_for_status.return_value = None
    resp.json.return_value = {"data": {"result": jobs, "meta": {"page": page + 1, "pages": pages}}}
    return resp


def _detail_response(fields=None):
    resp = MagicMock()
    resp.raise_for_status.return_value = None
    resp.json.return_value = {"data": fields or {}}
    return resp


def _mock_routed_client(list_responses, detail_responses=None):
    """
    Routes by call shape rather than a fixed call sequence: the list-page
    fetch always passes `params={...}`, the per-job detail fetch
    (_fetch_job_detail_fields) never does -- so this doesn't depend on
    exactly how many detail calls happen between page calls.
    """
    list_iter = iter(list_responses)
    detail_responses = detail_responses or {}

    async def _get(url, params=None, **kwargs):
        if params is not None:
            return next(list_iter)
        job_id = url.rsplit("/", 1)[-1]
        return detail_responses.get(job_id, _detail_response({}))

    mock_client = MagicMock()
    mock_client.get = AsyncMock(side_effect=_get)
    mock_client.__aenter__ = AsyncMock(return_value=mock_client)
    mock_client.__aexit__ = AsyncMock(return_value=False)
    return mock_client


def _embedding_generator():
    gen = MagicMock()
    gen.encode_job_fields.return_value = (
        np.array([0.1]),
        {"job_description_text": np.array([0.1])},
        [True] + [False] * (len(JOB_FIELDS) - 1),
    )
    return gen


class TestSyncJobsFromService:
    async def test_syncs_all_jobs_across_single_page(self, monkeypatch):
        faiss_manager = MagicMock()
        embedding_generator = _embedding_generator()

        jobs = [{"postId": "job-1", "title": "A"}, {"postId": "job-2", "title": "B"}]
        client = _mock_routed_client([_page_response(jobs)])
        monkeypatch.setattr("httpx.AsyncClient", lambda **kw: client)

        count = await sync_jobs_from_service(faiss_manager, embedding_generator)
        assert count == 2
        assert faiss_manager.add_job_with_embedding.call_count == 2

    async def test_detail_fields_merged_into_faiss_metadata(self, monkeypatch):
        faiss_manager = MagicMock()
        embedding_generator = _embedding_generator()

        jobs = [{"postId": "job-1", "title": "A"}]
        detail = {"job-1": _detail_response({"requirements": "Python", "responsibilities": "Build APIs"})}
        client = _mock_routed_client([_page_response(jobs)], detail_responses=detail)
        monkeypatch.setattr("httpx.AsyncClient", lambda **kw: client)

        await sync_jobs_from_service(faiss_manager, embedding_generator)
        stored_job_data = faiss_manager.add_job_with_embedding.call_args.args[2]
        assert stored_job_data["requirements"] == "Python"
        assert stored_job_data["responsibilities"] == "Build APIs"

    async def test_detail_fetch_failure_is_non_fatal(self, monkeypatch):
        faiss_manager = MagicMock()
        embedding_generator = _embedding_generator()

        jobs = [{"postId": "job-1", "title": "A"}]
        list_iter = iter([_page_response(jobs)])

        async def _get(url, params=None, **kwargs):
            if params is not None:
                return next(list_iter)
            raise httpx.TimeoutException("detail endpoint slow")

        client = MagicMock()
        client.get = AsyncMock(side_effect=_get)
        client.__aenter__ = AsyncMock(return_value=client)
        client.__aexit__ = AsyncMock(return_value=False)
        monkeypatch.setattr("httpx.AsyncClient", lambda **kw: client)

        count = await sync_jobs_from_service(faiss_manager, embedding_generator)
        assert count == 1  # job still synced, just without the detail fields

    async def test_skips_job_missing_post_id(self, monkeypatch):
        faiss_manager = MagicMock()
        embedding_generator = _embedding_generator()
        jobs = [{"title": "No ID"}]
        client = _mock_routed_client([_page_response(jobs)])
        monkeypatch.setattr("httpx.AsyncClient", lambda **kw: client)

        count = await sync_jobs_from_service(faiss_manager, embedding_generator)
        assert count == 0

    async def test_per_job_error_is_non_fatal(self, monkeypatch):
        faiss_manager = MagicMock()
        faiss_manager.add_job_with_embedding.side_effect = [RuntimeError("boom"), None]
        embedding_generator = _embedding_generator()
        jobs = [{"postId": "job-1"}, {"postId": "job-2"}]
        client = _mock_routed_client([_page_response(jobs)])
        monkeypatch.setattr("httpx.AsyncClient", lambda **kw: client)

        count = await sync_jobs_from_service(faiss_manager, embedding_generator)
        assert count == 1  # job-1 failed, job-2 succeeded

    async def test_empty_page_stops_pagination(self, monkeypatch):
        faiss_manager = MagicMock()
        embedding_generator = _embedding_generator()
        client = _mock_routed_client([_page_response([])])
        monkeypatch.setattr("httpx.AsyncClient", lambda **kw: client)

        count = await sync_jobs_from_service(faiss_manager, embedding_generator)
        assert count == 0

    async def test_http_status_error_stops_sync(self, monkeypatch):
        faiss_manager = MagicMock()
        embedding_generator = _embedding_generator()

        def _raise(*a, **kw):
            resp = MagicMock(status_code=500, text="server error")
            raise httpx.HTTPStatusError("error", request=MagicMock(), response=resp)

        client = _mock_async_client(_raise)
        monkeypatch.setattr("httpx.AsyncClient", lambda **kw: client)

        count = await sync_jobs_from_service(faiss_manager, embedding_generator)
        assert count == 0

    async def test_connect_error_retries_then_gives_up(self, monkeypatch):
        faiss_manager = MagicMock()
        embedding_generator = _embedding_generator()

        def _raise(*a, **kw):
            raise httpx.ConnectError("unreachable")

        client = _mock_async_client(_raise)
        monkeypatch.setattr("httpx.AsyncClient", lambda **kw: client)
        monkeypatch.setattr("app.services.job_sync._MAX_SYNC_RETRIES", 2)
        monkeypatch.setattr("app.services.job_sync.asyncio.sleep", AsyncMock())

        count = await sync_jobs_from_service(faiss_manager, embedding_generator)
        assert count == 0

    async def test_multi_page_pagination(self, monkeypatch):
        faiss_manager = MagicMock()
        embedding_generator = _embedding_generator()
        page1 = _page_response([{"postId": "job-1"}], page=0, pages=2)
        page2 = _page_response([{"postId": "job-2"}], page=1, pages=2)
        client = _mock_routed_client([page1, page2])
        monkeypatch.setattr("httpx.AsyncClient", lambda **kw: client)

        count = await sync_jobs_from_service(faiss_manager, embedding_generator)
        assert count == 2
