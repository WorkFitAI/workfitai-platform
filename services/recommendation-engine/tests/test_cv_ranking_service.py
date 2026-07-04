"""
Tests for app/services/cv_ranking_service.py (build_pipeline, rank_resumes).

build_pipeline: load_config/CVRankingPipeline are patched at the names imported
into this module — the real pipeline constructor loads real model weights.

rank_resumes: uses a real CVRankingPipeline instance created via __new__ (no
__init__, no model loading) with `rank` stubbed — `to_dict` is real code
(pure data transformation, no model access) so it's exercised for real.
"""

from unittest.mock import MagicMock

from src.inference.pipeline import CVRankingPipeline, RankedResume, RankResult

from app.models.cv_rank_requests import CvRankRequest, JobRequest, OptionsRequest, ResumeRequest
from app.services.cv_ranking_service import build_pipeline, rank_resumes


class TestBuildPipeline:
    def test_loads_config_and_constructs_pipeline(self, monkeypatch):
        fake_config = {"model_dir": "/models/cv-refer"}
        fake_pipeline_instance = MagicMock(name="pipeline_instance")

        monkeypatch.setattr(
            "app.services.cv_ranking_service.load_config", lambda path: fake_config
        )
        monkeypatch.setattr(
            "app.services.cv_ranking_service.CVRankingPipeline",
            lambda cfg: fake_pipeline_instance if cfg is fake_config else None,
        )
        result = build_pipeline("/some/config.yaml")
        assert result is fake_pipeline_instance


def _pipeline_stub() -> CVRankingPipeline:
    """Bypass __init__ (no model loading) — only `rank`/`to_dict` are exercised."""
    return CVRankingPipeline.__new__(CVRankingPipeline)


def _request() -> CvRankRequest:
    return CvRankRequest(
        job=JobRequest(
            job_index=1,
            jd_overview="Backend role",
            jd_requirements="Python",
            jd_responsibilities="Build APIs",
        ),
        resumes=[
            ResumeRequest(
                resume_index=0,
                resume_summary="Experienced dev",
                resume_experience="5 years",
                resume_skills="Python",
            )
        ],
        options=OptionsRequest(top_k=10, top_n=5, min_score=20.0, good_fit_threshold=55.0),
    )


class TestRankResumes:
    def test_translates_request_and_serializes_result(self):
        pipeline = _pipeline_stub()
        expected_result = RankResult(
            job_index=1,
            job_overview="Backend role",
            total_candidates=1,
            processing_time_ms=42.0,
            ranked_resumes=[
                RankedResume(
                    resume_index=0, score=88.0, similarity_score=85.0,
                    cross_score=90.0, label="Good Fit", explanation="Strong match.",
                )
            ],
        )
        pipeline.rank = MagicMock(return_value=expected_result)

        result_dict = rank_resumes(pipeline, _request())

        assert result_dict["job_index"] == 1
        assert result_dict["total_candidates"] == 1
        assert result_dict["ranked_resumes"][0]["label"] == "Good Fit"
        # Confirms JobInput/ResumeInput translation reached the pipeline correctly.
        call_kwargs = pipeline.rank.call_args.kwargs
        assert call_kwargs["job"].jd_overview == "Backend role"
        assert call_kwargs["resumes"][0].resume_summary == "Experienced dev"
        assert call_kwargs["top_k"] == 10
        assert call_kwargs["min_score"] == 20.0

    def test_passes_precomputed_embeddings_by_index(self):
        pipeline = _pipeline_stub()
        pipeline.rank = MagicMock(
            return_value=RankResult(
                job_index=1, job_overview="", total_candidates=1,
                processing_time_ms=1.0, ranked_resumes=[],
            )
        )
        embeddings_by_index = {0: [0.1, 0.2, 0.3]}

        rank_resumes(pipeline, _request(), embeddings_by_index)

        call_kwargs = pipeline.rank.call_args.kwargs
        assert call_kwargs["resume_embeddings"] == [[0.1, 0.2, 0.3]]

    def test_no_embeddings_passes_none(self):
        pipeline = _pipeline_stub()
        pipeline.rank = MagicMock(
            return_value=RankResult(
                job_index=1, job_overview="", total_candidates=1,
                processing_time_ms=1.0, ranked_resumes=[],
            )
        )
        rank_resumes(pipeline, _request(), embeddings_by_index=None)
        call_kwargs = pipeline.rank.call_args.kwargs
        assert call_kwargs["resume_embeddings"] is None
