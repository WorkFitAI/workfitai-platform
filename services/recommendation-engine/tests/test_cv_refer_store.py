"""Direct unit tests for app/services/cv_refer_store.py (CvReferStore + get_store singleton)."""

from app.services.cv_refer_store import CvReferStore, get_store


class TestApplicantPool:
    def test_add_and_get_applicant_usernames(self):
        store = CvReferStore()
        store.add_applicant("job-1", "alice")
        store.add_applicant("job-1", "bob")
        assert set(store.get_applicant_usernames("job-1")) == {"alice", "bob"}

    def test_get_applicant_usernames_empty_for_unknown_job(self):
        store = CvReferStore()
        assert store.get_applicant_usernames("no-such-job") == []

    def test_remove_applicant_from_existing_pool(self):
        store = CvReferStore()
        store.add_applicant("job-1", "alice")
        store.remove_applicant("job-1", "alice")
        assert store.get_applicant_usernames("job-1") == []

    def test_remove_applicant_from_untracked_job_is_noop(self):
        store = CvReferStore()
        # No pool exists for job-1 yet — must not raise (covers the `if pool:` False branch).
        store.remove_applicant("job-1", "alice")
        assert store.get_applicant_usernames("job-1") == []

    def test_remove_applicant_clears_cv_data_and_embedding(self):
        store = CvReferStore()
        store.add_applicant("job-1", "alice")
        store.set_cv_snapshot("job-1", "alice", {"summary": "s"})
        store.set_cv_embedding("job-1", "alice", [0.1])
        store.remove_applicant("job-1", "alice")
        assert store.get_cv_data("job-1", "alice") is None
        assert store.get_cv_embedding("job-1", "alice") is None


class TestCvSnapshotAndEmbedding:
    def test_set_and_get_cv_data(self):
        store = CvReferStore()
        snapshot = {"summary": "s", "experience": "e", "skills": "sk", "education": "ed"}
        store.set_cv_snapshot("job-1", "alice", snapshot)
        assert store.get_cv_data("job-1", "alice") == snapshot

    def test_get_cv_data_missing_returns_none(self):
        store = CvReferStore()
        assert store.get_cv_data("job-1", "alice") is None

    def test_set_cv_snapshot_invalidates_stale_embedding(self):
        store = CvReferStore()
        store.set_cv_embedding("job-1", "alice", [0.1, 0.2])
        store.set_cv_snapshot("job-1", "alice", {"summary": "new"})
        assert store.get_cv_embedding("job-1", "alice") is None

    def test_get_cv_embedding_missing_returns_none(self):
        store = CvReferStore()
        assert store.get_cv_embedding("job-1", "alice") is None


class TestStatusChanged:
    def test_terminal_status_removes_applicant(self):
        store = CvReferStore()
        store.add_applicant("job-1", "alice")
        store.on_status_changed("job-1", "alice", "REJECTED")
        assert "alice" not in store.get_applicant_usernames("job-1")

    def test_terminal_status_case_insensitive(self):
        store = CvReferStore()
        store.add_applicant("job-1", "alice")
        store.on_status_changed("job-1", "alice", "hired")
        assert "alice" not in store.get_applicant_usernames("job-1")

    def test_non_terminal_status_keeps_applicant(self):
        store = CvReferStore()
        store.add_applicant("job-1", "alice")
        store.on_status_changed("job-1", "alice", "REVIEWING")
        assert "alice" in store.get_applicant_usernames("job-1")


class TestStats:
    def test_get_stats_reflects_pool_and_cv_counts(self):
        store = CvReferStore()
        store.add_applicant("job-1", "alice")
        store.add_applicant("job-1", "bob")
        store.set_cv_snapshot("job-1", "alice", {"summary": "s"})
        store.set_cv_embedding("job-1", "alice", [0.1])
        stats = store.get_stats()
        assert stats["total_jobs_tracked"] == 1
        assert stats["total_cv_profiles"] == 1
        assert stats["total_cv_embeddings"] == 1
        assert stats["job_pool_sizes"] == {"job-1": 2}


class TestSingleton:
    def test_get_store_returns_same_instance(self):
        import app.services.cv_refer_store as module

        module._store = None
        first = get_store()
        second = get_store()
        assert first is second
        module._store = None
