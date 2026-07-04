"""
Tests for app/services/faiss_manager.py (FAISSIndexManager).

Uses the REAL faiss library with a small dimension (4) and tiny numpy vectors —
faiss.IndexFlatIP is pure vector math (no trained model, no I/O), so mocking it
would just re-implement it. This exercises the manager's actual decision logic
(id mapping, filters, save/load round-trip, error handling) for real.
"""

import numpy as np
import pytest

from app.services.faiss_manager import FAISSIndexManager

DIM = 4


def _vec(*values):
    arr = np.array(values, dtype=np.float32)
    norm = np.linalg.norm(arr)
    return (arr / norm) if norm > 0 else arr


class TestConstruction:
    def test_empty_index_created(self):
        manager = FAISSIndexManager(dimension=DIM)
        assert manager.index.ntotal == 0
        assert manager.dimension == DIM

    def test_load_existing_index_path_ignored_when_missing(self, tmp_path):
        missing_path = str(tmp_path / "does-not-exist.index")
        manager = FAISSIndexManager(dimension=DIM, index_path=missing_path)
        assert manager.index.ntotal == 0

    def test_load_existing_index_from_disk(self, tmp_path):
        path = str(tmp_path / "faiss.index")
        first = FAISSIndexManager(dimension=DIM)
        first.add_job_with_embedding("job-1", _vec(1, 0, 0, 0), {"title": "A"})
        first.save_index(path)

        second = FAISSIndexManager(dimension=DIM, index_path=path)
        assert second.index.ntotal == 1
        assert second.get_job_by_id("job-1") is not None

    def test_corrupt_index_file_falls_back_to_empty(self, tmp_path):
        path = tmp_path / "corrupt.index"
        path.write_text("not a real faiss index")
        manager = FAISSIndexManager(dimension=DIM, index_path=str(path))
        assert manager.index.ntotal == 0


class TestAddJobWithEmbedding:
    def test_add_increases_index_size(self):
        manager = FAISSIndexManager(dimension=DIM)
        manager.add_job_with_embedding("job-1", _vec(1, 0, 0, 0), {"title": "A"})
        assert manager.index.ntotal == 1
        assert manager.job_id_to_id["job-1"] == 0

    def test_add_reshapes_1d_embedding(self):
        manager = FAISSIndexManager(dimension=DIM)
        embedding_1d = _vec(1, 0, 0, 0)
        assert embedding_1d.ndim == 1
        manager.add_job_with_embedding("job-1", embedding_1d, {})
        assert manager.index.ntotal == 1

    def test_add_existing_job_id_updates_instead(self):
        manager = FAISSIndexManager(dimension=DIM)
        manager.add_job_with_embedding("job-1", _vec(1, 0, 0, 0), {"title": "Old"})
        manager.add_job_with_embedding("job-1", _vec(0, 1, 0, 0), {"title": "New"})
        assert manager.job_metadata["job-1"]["title"] == "New"


class TestAddJobTextOnly:
    def test_add_job_stores_text_metadata_without_faiss_vector(self):
        manager = FAISSIndexManager(dimension=DIM)
        manager.add_job("job-1", "job text", {"title": "A"})
        assert manager.job_id_to_id["job-1"] == 0
        assert manager.job_metadata["job-1"]["job_text"] == "job text"
        # add_job doesn't touch the FAISS index itself (embedding added separately)
        assert manager.index.ntotal == 0

    def test_add_job_existing_id_updates_instead(self):
        manager = FAISSIndexManager(dimension=DIM)
        manager.add_job("job-1", "old text", {})
        manager.add_job("job-1", "new text", {})
        assert manager.job_metadata["job-1"]["job_text"] == "new text"


class TestUpdateJob:
    def test_update_existing_job_with_embedding(self):
        manager = FAISSIndexManager(dimension=DIM)
        manager.add_job_with_embedding("job-1", _vec(1, 0, 0, 0), {"title": "Old"})
        manager.update_job_with_embedding("job-1", _vec(0, 1, 0, 0), {"title": "New"})
        assert manager.job_metadata["job-1"]["title"] == "New"
        # Documented behavior: remove_job() doesn't delete the raw FAISS vector
        # (no efficient removal in FAISS), only the id mappings — so ntotal grows
        # by one even though only one job_id is reachable via the mappings.
        assert manager.index.ntotal == 2
        assert len(manager.job_id_to_id) == 1

    def test_update_nonexistent_job_adds_it(self):
        manager = FAISSIndexManager(dimension=DIM)
        manager.update_job_with_embedding("job-1", _vec(1, 0, 0, 0), {"title": "New"})
        assert manager.job_id_to_id["job-1"] == 0

    def test_update_job_text_only_nonexistent_adds(self):
        manager = FAISSIndexManager(dimension=DIM)
        manager.update_job("job-1", "text", {"title": "New"})
        assert manager.job_id_to_id["job-1"] == 0

    def test_update_job_text_only_existing_removes_then_readds(self):
        manager = FAISSIndexManager(dimension=DIM)
        manager.add_job("job-1", "old", {"title": "Old"})
        manager.update_job("job-1", "new", {"title": "New"})
        assert manager.job_metadata["job-1"]["job_text"] == "new"


class TestRemoveJob:
    def test_remove_existing_job(self):
        manager = FAISSIndexManager(dimension=DIM)
        manager.add_job_with_embedding("job-1", _vec(1, 0, 0, 0), {})
        manager.remove_job("job-1")
        assert "job-1" not in manager.job_id_to_id
        assert manager.get_job_by_id("job-1") is None

    def test_remove_nonexistent_job_is_noop(self):
        manager = FAISSIndexManager(dimension=DIM)
        manager.remove_job("no-such-job")  # must not raise


class TestSearch:
    def test_empty_index_returns_empty(self):
        manager = FAISSIndexManager(dimension=DIM)
        results = manager.search(_vec(1, 0, 0, 0))
        assert results == []

    def test_search_returns_ranked_matches(self):
        manager = FAISSIndexManager(dimension=DIM)
        manager.add_job_with_embedding("job-close", _vec(1, 0, 0, 0), {"title": "Close"})
        manager.add_job_with_embedding("job-far", _vec(0, 0, 0, 1), {"title": "Far"})

        results = manager.search(_vec(1, 0, 0, 0), top_k=2)
        assert results[0]["jobId"] == "job-close"

    def test_search_reshapes_1d_query(self):
        manager = FAISSIndexManager(dimension=DIM)
        manager.add_job_with_embedding("job-1", _vec(1, 0, 0, 0), {})
        query = _vec(1, 0, 0, 0)
        assert query.ndim == 1
        results = manager.search(query)
        assert len(results) == 1

    def test_search_respects_top_k(self):
        manager = FAISSIndexManager(dimension=DIM)
        for i in range(5):
            manager.add_job_with_embedding(f"job-{i}", _vec(1, i * 0.01, 0, 0), {})
        results = manager.search(_vec(1, 0, 0, 0), top_k=2)
        assert len(results) == 2

    def test_search_with_location_filter(self):
        manager = FAISSIndexManager(dimension=DIM)
        manager.add_job_with_embedding("job-nyc", _vec(1, 0, 0, 0), {"location": "New York"})
        manager.add_job_with_embedding("job-remote", _vec(1, 0.01, 0, 0), {"location": "Remote"})
        results = manager.search(_vec(1, 0, 0, 0), top_k=5, filters={"location": "new york"})
        assert len(results) == 1
        assert results[0]["jobId"] == "job-nyc"

    def test_search_with_salary_filters(self):
        manager = FAISSIndexManager(dimension=DIM)
        manager.add_job_with_embedding("job-lo", _vec(1, 0, 0, 0), {"salaryMax": 50000, "salaryMin": 40000})
        manager.add_job_with_embedding("job-hi", _vec(1, 0.01, 0, 0), {"salaryMax": 150000, "salaryMin": 100000})
        results = manager.search(_vec(1, 0, 0, 0), top_k=5, filters={"minSalary": 90000})
        assert [r["jobId"] for r in results] == ["job-hi"]

        results = manager.search(_vec(1, 0, 0, 0), top_k=5, filters={"maxSalary": 60000})
        assert [r["jobId"] for r in results] == ["job-lo"]

    def test_search_with_experience_and_job_type_filters(self):
        manager = FAISSIndexManager(dimension=DIM)
        manager.add_job_with_embedding(
            "job-senior", _vec(1, 0, 0, 0), {"experienceLevel": "SENIOR", "jobType": "FULL_TIME"}
        )
        manager.add_job_with_embedding(
            "job-junior", _vec(1, 0.01, 0, 0), {"experienceLevel": "JUNIOR", "jobType": "PART_TIME"}
        )
        results = manager.search(_vec(1, 0, 0, 0), top_k=5, filters={"experienceLevel": "SENIOR"})
        assert [r["jobId"] for r in results] == ["job-senior"]

        results = manager.search(_vec(1, 0, 0, 0), top_k=5, filters={"jobType": "PART_TIME"})
        assert [r["jobId"] for r in results] == ["job-junior"]

    def test_search_error_returns_empty(self):
        manager = FAISSIndexManager(dimension=DIM)
        manager.add_job_with_embedding("job-1", _vec(1, 0, 0, 0), {})
        # Wrong-dimension query triggers a FAISS/numpy error, caught internally.
        bad_query = np.array([1.0, 0.0], dtype=np.float32)
        results = manager.search(bad_query)
        assert results == []


class TestGetJobById:
    def test_returns_none_for_missing_job(self):
        manager = FAISSIndexManager(dimension=DIM)
        assert manager.get_job_by_id("missing") is None

    def test_returns_job_with_embedding_and_metadata(self):
        manager = FAISSIndexManager(dimension=DIM)
        manager.add_job_with_embedding("job-1", _vec(1, 0, 0, 0), {"title": "A"})
        result = manager.get_job_by_id("job-1")
        assert result["jobId"] == "job-1"
        assert result["title"] == "A"
        assert result["embedding"].shape == (DIM,)


class TestSaveAndLoad:
    def test_save_without_path_is_noop(self):
        manager = FAISSIndexManager(dimension=DIM)
        manager.save_index()  # no index_path configured — must not raise

    def test_save_and_load_round_trip(self, tmp_path):
        path = str(tmp_path / "index" / "faiss.index")
        manager = FAISSIndexManager(dimension=DIM)
        manager.add_job_with_embedding("job-1", _vec(1, 0, 0, 0), {"title": "A"})
        manager.save_index(path)

        loaded = FAISSIndexManager(dimension=DIM)
        loaded.load_index(path)
        assert loaded.index.ntotal == 1
        assert loaded.job_metadata["job-1"]["title"] == "A"

    def test_load_missing_file_raises(self, tmp_path):
        manager = FAISSIndexManager(dimension=DIM)
        with pytest.raises(Exception):
            manager.load_index(str(tmp_path / "missing.index"))


class TestStats:
    def test_get_stats(self):
        manager = FAISSIndexManager(dimension=DIM)
        manager.add_job_with_embedding("job-1", _vec(1, 0, 0, 0), {})
        stats = manager.get_stats()
        assert stats["total_jobs"] == 1
        assert stats["vectors_in_index"] == 1
        assert stats["dimension"] == DIM
