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
