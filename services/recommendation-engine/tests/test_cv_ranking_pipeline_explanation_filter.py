"""
Tests for Stage 3 explanation-quality filtering in CVRankingPipeline.

A candidate whose CV is missing sections the model needs can produce a degenerate
explanation containing "failed" (sparse/empty input to T5). Such candidates must be
excluded from ranked_resumes — treated as not ranked — instead of surfacing a broken
explanation as a normal ranked result.

Pipeline instantiation is bypassed (__new__, no __init__) to avoid loading real model
weights; only _stage3_explain/_explain are under test, so no other instance state is needed.
"""

from src.inference.pipeline import CVRankingPipeline


def _make_pipeline() -> CVRankingPipeline:
    return CVRankingPipeline.__new__(CVRankingPipeline)


def _candidate(resume_index: int, score: float) -> dict:
    return {
        "resume_index": resume_index,
        "score": score,
        "similarity_score": score,
        "cross_score": score,
    }


class TestExplanationFailureFilter:
    def test_failed_explanation_excludes_candidate(self):
        pipe = _make_pipeline()
        pipe._explain = lambda candidate, label: "Explanation generation failed for this candidate."

        results = pipe._stage3_explain(
            [_candidate(1, 80.0)], good_fit_threshold=55.0, min_score=20.0
        )
        assert results == []

    def test_failed_is_case_insensitive(self):
        pipe = _make_pipeline()
        pipe._explain = lambda candidate, label: "FAILED to assess candidate fit."

        results = pipe._stage3_explain(
            [_candidate(1, 80.0)], good_fit_threshold=55.0, min_score=20.0
        )
        assert results == []

    def test_normal_explanation_is_not_filtered(self):
        pipe = _make_pipeline()
        pipe._explain = lambda candidate, label: "Strong match across skills and experience."

        results = pipe._stage3_explain(
            [_candidate(1, 80.0)], good_fit_threshold=55.0, min_score=20.0
        )
        assert len(results) == 1
        assert results[0].resume_index == 1
        assert results[0].explanation == "Strong match across skills and experience."

    def test_mixed_batch_keeps_only_valid_explanations(self):
        pipe = _make_pipeline()
        explanations = {
            1: "Good alignment with key requirements.",
            2: "Explanation generation failed.",
            3: "Partial match; has some relevant skills.",
        }
        pipe._explain = lambda candidate, label: explanations[candidate["resume_index"]]

        candidates = [_candidate(1, 80.0), _candidate(2, 80.0), _candidate(3, 60.0)]
        results = pipe._stage3_explain(candidates, good_fit_threshold=55.0, min_score=20.0)

        result_indices = {r.resume_index for r in results}
        assert result_indices == {1, 3}

    def test_below_min_score_still_excluded_independently_of_explanation_filter(self):
        """Existing min_score filtering must keep working alongside the new filter."""
        pipe = _make_pipeline()
        pipe._explain = lambda candidate, label: "Strong match across skills and experience."

        results = pipe._stage3_explain(
            [_candidate(1, 10.0)], good_fit_threshold=55.0, min_score=20.0
        )
        assert results == []
