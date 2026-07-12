"""
Shared pytest fixtures for recommendation-engine tests.

Key seams provided here:
- `client`: FastAPI TestClient wired with mocked/fake dependencies. Deliberately
  NOT entered as a context manager, so the real `lifespan` in app/main.py (which
  loads real ML models and connects to Kafka/Vault) never runs — starlette's
  TestClient only sends ASGI lifespan startup/shutdown events when used as
  `with TestClient(app) as c:` (see starlette.testclient.TestClient.__enter__).
  A bare `TestClient(app)` never triggers it, so app_state stays exactly as we
  set it below.
- Mocks for faiss_manager, embedding_generator (aliased "model" in app_state),
  resume_parser, reranker, cv_ranking_pipeline — the heavy ML/IO boundaries.
- Real (lightweight, pure in-memory) instances of CvReferStore and
  FeatureToggleStore — no reason to mock plain-Python data structures.
- Cache singletons (recommendation_cache, cv_ranking_cache) are reset between
  tests so one test's cached result can't leak into another.

VAULT_ENABLED is forced to "false" before importing app.main so app.config's
module-level Vault bootstrap doesn't attempt a real (slow, always-failing in CI)
network call to a "vault" hostname that doesn't resolve outside Docker.
"""

import os

os.environ.setdefault("VAULT_ENABLED", "false")
os.environ.setdefault("ENABLE_KAFKA_CONSUMER", "false")
os.environ.setdefault("ENABLE_INITIAL_SYNC", "false")
os.environ.setdefault("ENABLE_CV_REFER_INITIAL_SYNC", "false")

from unittest.mock import MagicMock

import numpy as np
import pytest
from fastapi.testclient import TestClient

import app.main as main_module
from app.services import cv_ranking_cache as cv_ranking_cache_module
from app.services import recommendation_cache as recommendation_cache_module
from app.services.cv_refer_store import CvReferStore
from app.services.feature_toggle_store import FeatureToggleStore

FAKE_EMBEDDING_DIM = 4


@pytest.fixture(autouse=True)
def _reset_singleton_caches():
    """
    Reset module-level cache singletons before/after each test so a cached
    result computed by one test can never be served to another (both caches
    are keyed by content hash / job_id, but this keeps tests fully isolated).
    """
    recommendation_cache_module._cache = None
    cv_ranking_cache_module._cache = None
    yield
    recommendation_cache_module._cache = None
    cv_ranking_cache_module._cache = None


@pytest.fixture(autouse=True)
def _reset_app_state():
    """Restore app_state to its pre-test snapshot after every test."""
    original = dict(main_module.app_state)
    yield
    main_module.app_state.clear()
    main_module.app_state.update(original)


@pytest.fixture
def app_state():
    """Direct handle to app.main.app_state for tests that wire specific components."""
    return main_module.app_state


@pytest.fixture
def mock_faiss_manager():
    mock = MagicMock(name="faiss_manager")
    mock.index.ntotal = 1
    mock.search.return_value = []
    mock.get_job_by_id.return_value = None
    # {} (not MagicMock's default empty-iterating auto-mock) so callers doing
    # `get_job_field_embeddings(...) or {}` get a real, safely-iterable dict.
    mock.get_job_field_embeddings.return_value = {}
    return mock


@pytest.fixture
def mock_embedding_generator():
    mock = MagicMock(name="embedding_generator")
    mock.encode_resume.return_value = np.zeros(FAKE_EMBEDDING_DIM, dtype=np.float32)
    mock.encode_job.return_value = np.zeros(FAKE_EMBEDDING_DIM, dtype=np.float32)
    # Multi-field methods: (pooled, per_field_embeddings, presence_mask).
    # Only "resume_text" present by default -- individual tests override this
    # when they need specific fields marked present/absent.
    mock.encode_job_fields.return_value = (
        np.zeros(FAKE_EMBEDDING_DIM, dtype=np.float32),
        {"job_description_text": np.zeros(FAKE_EMBEDDING_DIM, dtype=np.float32)},
        [True, False, False, False, False],
    )
    mock.encode_resume_fields.return_value = (
        np.zeros(FAKE_EMBEDDING_DIM, dtype=np.float32),
        {"resume_text": np.zeros(FAKE_EMBEDDING_DIM, dtype=np.float32)},
        [True, False, False, False, False],
    )
    return mock


@pytest.fixture
def mock_resume_parser():
    mock = MagicMock(name="resume_parser")
    mock.parse_resume.return_value = {
        "raw_text": "Jane Doe resume text",
        "skills": ["Python"],
        "experience_years": 3,
        "education": "Bachelor",
        "contact_info": {},
    }
    mock.format_resume_for_matching.return_value = "Skills: Python\nExperience: 3 years"
    return mock


@pytest.fixture
def mock_reranker():
    mock = MagicMock(name="reranker")
    mock.rerank.side_effect = lambda resume_text, candidates, top_n, resume_fields=None: candidates[:top_n]
    return mock


@pytest.fixture
def mock_cv_ranking_pipeline():
    mock = MagicMock(name="cv_ranking_pipeline")
    mock.is_ready = True
    return mock


@pytest.fixture
def cv_refer_store():
    """Real (in-memory, no I/O) store — mocking it would just re-implement it."""
    return CvReferStore()


@pytest.fixture
def feature_toggle_store():
    """Real store; unset keys default to enabled (documented cold-start behavior)."""
    return FeatureToggleStore()


@pytest.fixture
def mock_cv_ranking_refresher():
    return MagicMock(name="cv_ranking_refresher")


@pytest.fixture
def wired_app_state(
    app_state,
    mock_embedding_generator,
    mock_faiss_manager,
    mock_resume_parser,
    mock_reranker,
    mock_cv_ranking_pipeline,
    cv_refer_store,
    feature_toggle_store,
    mock_cv_ranking_refresher,
):
    """Populate app_state with every mocked/fake dependency routes read from."""
    app_state.update(
        {
            "model": mock_embedding_generator,
            "faiss_manager": mock_faiss_manager,
            "resume_parser": mock_resume_parser,
            "reranker": mock_reranker,
            "cv_ranking_pipeline": mock_cv_ranking_pipeline,
            "cv_refer_store": cv_refer_store,
            "cv_ranking_refresher": mock_cv_ranking_refresher,
            "feature_toggle_store": feature_toggle_store,
        }
    )
    return app_state


@pytest.fixture
def client(wired_app_state):
    """
    TestClient with every route dependency wired to a mock/fake — see module
    docstring for why the real lifespan never runs here.
    """
    return TestClient(main_module.app)
