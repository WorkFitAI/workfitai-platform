"""
Tests for Stage 3 (label assignment + explanation + min_score filter) in CVRankingPipeline.

Note: the "failed"/"error" content guard lives one layer down, in `_explain`'s use of
`_is_valid_t5_explanation` (src/inference/pipeline.py), which prevents a degenerate T5
output from ever being returned to the caller — it falls back to a rule-based explanation
instead. `_stage3_explain` itself trusts whatever `_explain` returns and does not re-inspect
the explanation text; these tests stub `_explain` directly (bypassing that guard) purely to
exercise `_stage3_explain`'s own responsibilities: label assignment and min_score filtering.

Pipeline instantiation is bypassed (__new__, no __init__) to avoid loading real model
weights; only _stage3_explain/_explain are under test, so no other instance state is needed.
"""

from src.inference.pipeline import CVRankingPipeline, ResumeInput


def _make_pipeline() -> CVRankingPipeline:
    return CVRankingPipeline.__new__(CVRankingPipeline)


def _candidate(resume_index: int, score: float) -> dict:
    return {
        "resume_index": resume_index,
        "score": score,
        "similarity_score": score,
        "cross_score": score,
        "_resume": ResumeInput(
            resume_index=resume_index,
            resume_summary="Experienced developer",
            resume_experience="3 years Python",
            resume_skills="Python, FastAPI",
            resume_education="BSc Computer Science",
        ),
    }


class TestExplanationFailureFilter:
    def test_explain_output_is_passed_through_unfiltered(self):
        """_stage3_explain does not re-validate _explain's output (that guard is _explain's job)."""
        pipe = _make_pipeline()
        pipe._explain = lambda candidate, label: "Explanation generation failed for this candidate."

        results = pipe._stage3_explain(
            [_candidate(1, 80.0)], good_fit_threshold=55.0, min_score=20.0
        )
        assert len(results) == 1
        assert results[0].explanation == "Explanation generation failed for this candidate."

    def test_label_is_case_insensitive_to_explanation_content(self):
        pipe = _make_pipeline()
        pipe._explain = lambda candidate, label: "FAILED to assess candidate fit."

        results = pipe._stage3_explain(
            [_candidate(1, 80.0)], good_fit_threshold=55.0, min_score=20.0
        )
        assert len(results) == 1
        assert results[0].label == "Good Fit"

    def test_normal_explanation_is_not_filtered(self):
        pipe = _make_pipeline()
        pipe._explain = lambda candidate, label: "Strong match across skills and experience."

        results = pipe._stage3_explain(
            [_candidate(1, 80.0)], good_fit_threshold=55.0, min_score=20.0
        )
        assert len(results) == 1
        assert results[0].resume_index == 1
        assert results[0].explanation == "Strong match across skills and experience."

    def test_mixed_batch_keeps_all_above_min_score_regardless_of_explanation_text(self):
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
        assert result_indices == {1, 2, 3}

    def test_below_min_score_still_excluded_independently_of_explanation_filter(self):
        """Existing min_score filtering must keep working alongside explanation pass-through."""
        pipe = _make_pipeline()
        pipe._explain = lambda candidate, label: "Strong match across skills and experience."

        results = pipe._stage3_explain(
            [_candidate(1, 10.0)], good_fit_threshold=55.0, min_score=20.0
        )
        assert results == []
