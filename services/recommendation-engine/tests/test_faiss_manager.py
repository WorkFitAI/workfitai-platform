"""
Tests for app/services/faiss_manager.py (FAISSIndexManager).

Uses the REAL faiss library with a small dimension (4) and tiny numpy vectors —
faiss.IndexFlatIP is pure vector math (no trained model, no I/O), so mocking it
would just re-implement it. This exercises the manager's actual decision logic
(id mapping, filters, save/load round-trip, error handling) for real.
"""

import os

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


class TestPerFieldStorage:
    def test_no_field_data_leaves_storage_empty(self):
        manager = FAISSIndexManager(dimension=DIM)
        manager.add_job_with_embedding("job-1", _vec(1, 0, 0, 0), {})
        assert manager.field_presence == {}
        assert manager.field_embeddings == {}
        assert manager.get_job_field_embeddings("job-1") == {}

    def test_field_data_stored_and_retrievable(self):
        manager = FAISSIndexManager(dimension=DIM)
        manager.add_job_with_embedding(
            "job-1", _vec(1, 0, 0, 0), {},
            field_embeddings={"jd_overview": _vec(0, 1, 0, 0), "jd_requirements": _vec(0, 0, 1, 0)},
            field_mask={"jd_overview": True, "jd_requirements": True, "jd_preferred": False},
        )
        result = manager.get_job_field_embeddings("job-1")
        assert set(result.keys()) == {"jd_overview", "jd_requirements"}
        np.testing.assert_array_equal(result["jd_overview"], _vec(0, 1, 0, 0))

    def test_absent_field_omitted_from_get_job_field_embeddings(self):
        manager = FAISSIndexManager(dimension=DIM)
        manager.add_job_with_embedding(
            "job-1", _vec(1, 0, 0, 0), {},
            field_embeddings={},
            field_mask={"jd_preferred": False},
        )
        assert manager.get_job_field_embeddings("job-1") == {}

    def test_row_alignment_when_later_job_lacks_field_data(self):
        """job-1 has field data, job-2 (added via a legacy-style call with no
        field_embeddings/field_mask) doesn't -- job-2's row must still be a
        False/zero placeholder, not a missing row, so job-3 stays aligned."""
        manager = FAISSIndexManager(dimension=DIM)
        manager.add_job_with_embedding(
            "job-1", _vec(1, 0, 0, 0), {},
            field_embeddings={"jd_overview": _vec(0, 1, 0, 0)},
            field_mask={"jd_overview": True},
        )
        manager.add_job_with_embedding("job-2", _vec(0, 0, 1, 0), {})
        manager.add_job_with_embedding(
            "job-3", _vec(0, 0, 0, 1), {},
            field_embeddings={"jd_overview": _vec(1, 0, 0, 0)},
            field_mask={"jd_overview": True},
        )

        assert len(manager.field_presence["jd_overview"]) == 3
        assert manager.field_presence["jd_overview"] == [True, False, True]
        assert manager.get_job_field_embeddings("job-2") == {}
        np.testing.assert_array_equal(
            manager.get_job_field_embeddings("job-3")["jd_overview"], _vec(1, 0, 0, 0)
        )

    def test_backfill_when_new_field_name_appears_later(self):
        """job-1 is added before "jd_preferred" is ever seen; once job-2
        introduces it, job-1's row for that field must backfill to False,
        not leave the two jobs' field_presence lists different lengths."""
        manager = FAISSIndexManager(dimension=DIM)
        manager.add_job_with_embedding(
            "job-1", _vec(1, 0, 0, 0), {},
            field_embeddings={"jd_overview": _vec(0, 1, 0, 0)},
            field_mask={"jd_overview": True},
        )
        manager.add_job_with_embedding(
            "job-2", _vec(0, 1, 0, 0), {},
            field_embeddings={"jd_preferred": _vec(0, 0, 1, 0)},
            field_mask={"jd_preferred": True},
        )
        assert manager.field_presence["jd_preferred"] == [False, True]
        assert len(manager.field_presence["jd_overview"]) == len(manager.field_presence["jd_preferred"])

    def test_get_job_field_embeddings_missing_job_returns_none(self):
        manager = FAISSIndexManager(dimension=DIM)
        assert manager.get_job_field_embeddings("no-such-job") is None

    def test_update_job_with_embedding_replaces_field_data(self):
        manager = FAISSIndexManager(dimension=DIM)
        manager.add_job_with_embedding(
            "job-1", _vec(1, 0, 0, 0), {},
            field_embeddings={"jd_overview": _vec(0, 1, 0, 0)},
            field_mask={"jd_overview": True},
        )
        manager.update_job_with_embedding(
            "job-1", _vec(0, 1, 0, 0), {},
            field_embeddings={"jd_overview": _vec(1, 0, 0, 0)},
            field_mask={"jd_overview": True},
        )
        np.testing.assert_array_equal(
            manager.get_job_field_embeddings("job-1")["jd_overview"], _vec(1, 0, 0, 0)
        )


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

    def test_save_and_load_without_field_data_writes_no_sidecar(self, tmp_path):
        path = str(tmp_path / "faiss.index")
        manager = FAISSIndexManager(dimension=DIM)
        manager.add_job_with_embedding("job-1", _vec(1, 0, 0, 0), {})
        manager.save_index(path)
        assert not os.path.exists(path + ".fields.npz")

        loaded = FAISSIndexManager(dimension=DIM)
        loaded.load_index(path)
        assert loaded.field_presence == {}

    def test_save_and_load_field_data_round_trip(self, tmp_path):
        path = str(tmp_path / "faiss.index")
        manager = FAISSIndexManager(dimension=DIM)
        manager.add_job_with_embedding(
            "job-1", _vec(1, 0, 0, 0), {"title": "A"},
            field_embeddings={"jd_overview": _vec(0, 1, 0, 0)},
            field_mask={"jd_overview": True, "jd_requirements": False},
        )
        manager.save_index(path)
        assert os.path.exists(path + ".fields.npz")

        loaded = FAISSIndexManager(dimension=DIM)
        loaded.load_index(path)
        assert loaded.field_presence["jd_overview"] == [True]
        assert loaded.field_presence["jd_requirements"] == [False]
        np.testing.assert_array_equal(loaded.field_embeddings["jd_overview"][0], _vec(0, 1, 0, 0))
        assert loaded.get_job_field_embeddings("job-1") is not None
        assert set(loaded.get_job_field_embeddings("job-1").keys()) == {"jd_overview"}


class TestStats:
    def test_get_stats(self):
        manager = FAISSIndexManager(dimension=DIM)
        manager.add_job_with_embedding("job-1", _vec(1, 0, 0, 0), {})
        stats = manager.get_stats()
        assert stats["total_jobs"] == 1
        assert stats["vectors_in_index"] == 1
        assert stats["dimension"] == DIM
