"""
Tests for app/services/job_embedding_service.py (EmbeddingGenerator).

SentenceTransformer is mocked at the constructor boundary — loading a real
E5-Large model is out of scope for a unit test; the encode_*/get_dimension
decision logic (prefixing, dtype, batching) is what's under test here.
"""

from unittest.mock import MagicMock

import numpy as np
import pytest

from app.services.job_embedding_service import EmbeddingGenerator


@pytest.fixture
def generator(monkeypatch):
    fake_model = MagicMock()
    fake_model.get_sentence_embedding_dimension.return_value = 4
    fake_model.max_seq_length = 512
    fake_model.encode.return_value = np.array([0.1, 0.2, 0.3, 0.4])
    monkeypatch.setattr(
        "app.services.job_embedding_service.SentenceTransformer", lambda path: fake_model
    )
    gen = EmbeddingGenerator("/fake/model/path")
    gen._fake_model = fake_model
    return gen


class TestConstruction:
    def test_load_failure_raises(self, monkeypatch):
        def _raise(path):
            raise OSError("model not found")

        monkeypatch.setattr("app.services.job_embedding_service.SentenceTransformer", _raise)
        with pytest.raises(OSError):
            EmbeddingGenerator("/missing/model")


class TestEncoding:
    def test_encode_job_uses_passage_prefix(self, generator):
        result = generator.encode_job("Backend role")
        call_args = generator._fake_model.encode.call_args
        assert call_args.args[0] == "passage: Backend role"
        assert result.dtype == np.float32

    def test_encode_resume_uses_query_prefix(self, generator):
        generator.encode_resume("Experienced dev")
        call_args = generator._fake_model.encode.call_args
        assert call_args.args[0] == "query: Experienced dev"

    def test_encode_batch_empty_returns_empty_array_with_dimension(self, generator):
        result = generator.encode_batch([])
        assert result.shape == (0, 4)

    def test_encode_batch_query_prefix(self, generator):
        generator._fake_model.encode.return_value = np.zeros((2, 4))
        generator.encode_batch(["a", "b"], is_query=True)
        call_args = generator._fake_model.encode.call_args
        assert call_args.args[0] == ["query: a", "query: b"]

    def test_encode_batch_passage_prefix_default(self, generator):
        generator._fake_model.encode.return_value = np.zeros((2, 4))
        generator.encode_batch(["a", "b"])
        call_args = generator._fake_model.encode.call_args
        assert call_args.args[0] == ["passage: a", "passage: b"]

    def test_get_dimension(self, generator):
        assert generator.get_dimension() == 4


class TestEncodeJobFields:
    FIELD_ORDER = ["job_description_text", "jd_overview", "jd_requirements"]

    def test_only_present_fields_are_encoded(self, generator):
        generator._fake_model.encode.return_value = np.zeros((2, 4), dtype=np.float32)
        fields = {"job_description_text": "Full JD", "jd_overview": "", "jd_requirements": "Python required"}
        generator.encode_job_fields(fields, self.FIELD_ORDER)
        call_args = generator._fake_model.encode.call_args
        assert call_args.args[0] == ["passage: Full JD", "passage: Python required"]

    def test_encode_job_fields_does_not_normalize_per_field(self, generator):
        generator._fake_model.encode.return_value = np.zeros((1, 4), dtype=np.float32)
        generator.encode_job_fields({"job_description_text": "JD"}, self.FIELD_ORDER)
        call_kwargs = generator._fake_model.encode.call_args.kwargs
        assert call_kwargs["normalize_embeddings"] is False

    def test_presence_mask_matches_field_order(self, generator):
        generator._fake_model.encode.return_value = np.zeros((2, 4), dtype=np.float32)
        fields = {"job_description_text": "Full JD", "jd_overview": None, "jd_requirements": "Python"}
        _, _, mask = generator.encode_job_fields(fields, self.FIELD_ORDER)
        assert mask == [True, False, True]

    def test_per_field_embeddings_keyed_by_present_field_name(self, generator):
        generator._fake_model.encode.return_value = np.array([[1.0, 0.0, 0.0, 0.0], [0.0, 1.0, 0.0, 0.0]], dtype=np.float32)
        fields = {"job_description_text": "Full JD", "jd_requirements": "Python"}
        _, per_field, _ = generator.encode_job_fields(fields, ["job_description_text", "jd_requirements"])
        assert set(per_field.keys()) == {"job_description_text", "jd_requirements"}
        np.testing.assert_array_equal(per_field["job_description_text"], [1.0, 0.0, 0.0, 0.0])
        np.testing.assert_array_equal(per_field["jd_requirements"], [0.0, 1.0, 0.0, 0.0])

    def test_pooled_embedding_is_masked_mean_then_normalized(self, generator):
        # Two orthogonal unit-length raw embeddings -> mean has norm 1/sqrt(2),
        # normalizing must bring it back to unit length pointing the same direction.
        generator._fake_model.encode.return_value = np.array(
            [[2.0, 0.0, 0.0, 0.0], [0.0, 2.0, 0.0, 0.0]], dtype=np.float32
        )
        fields = {"job_description_text": "Full JD", "jd_requirements": "Python"}
        pooled, _, _ = generator.encode_job_fields(fields, ["job_description_text", "jd_requirements"])
        expected_direction = np.array([1.0, 1.0, 0.0, 0.0]) / np.linalg.norm([1.0, 1.0, 0.0, 0.0])
        np.testing.assert_allclose(pooled, expected_direction, atol=1e-5)
        np.testing.assert_allclose(np.linalg.norm(pooled), 1.0, atol=1e-5)

    def test_no_fields_present_returns_zero_vector(self, generator):
        pooled, per_field, mask = generator.encode_job_fields(
            {"job_description_text": None, "jd_overview": ""}, ["job_description_text", "jd_overview"]
        )
        assert np.array_equal(pooled, np.zeros(4, dtype=np.float32))
        assert per_field == {}
        assert mask == [False, False]
        generator._fake_model.encode.assert_not_called()

    def test_single_present_field_still_returns_2d_shape_internally(self, generator):
        # encode() on a 1-item list can return a 1D array from SentenceTransformer;
        # must be handled without shape errors.
        generator._fake_model.encode.return_value = np.array([1.0, 0.0, 0.0, 0.0], dtype=np.float32)
        pooled, per_field, mask = generator.encode_job_fields(
            {"job_description_text": "Only this"}, ["job_description_text"]
        )
        assert mask == [True]
        np.testing.assert_allclose(np.linalg.norm(pooled), 1.0, atol=1e-5)


class TestEncodeResumeFields:
    FIELD_ORDER = ["resume_text", "resume_summary"]

    def test_uses_query_prefix(self, generator):
        generator._fake_model.encode.return_value = np.zeros((1, 4), dtype=np.float32)
        generator.encode_resume_fields({"resume_text": "Full resume"}, self.FIELD_ORDER)
        call_args = generator._fake_model.encode.call_args
        assert call_args.args[0] == ["query: Full resume"]
