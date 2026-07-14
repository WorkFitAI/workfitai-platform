import json
from pathlib import Path

import pytest

from scripts.cv_batch_generation import render_pdf_atomically, run_data_then_pdf_phases
from scripts.cv_output_naming import (
    application_cv_file,
    validate_unique_application_cv_files,
)
from scripts.cv_role_matching import (
    find_matching_roles,
    require_supported_matching_roles,
)


def test_application_cv_file_uses_cv_file_verbatim():
    assert application_cv_file({"cvFile": "custom-candidate-name.pdf"}) == (
        "custom-candidate-name.pdf",
        "custom-candidate-name.json",
    )


@pytest.mark.parametrize("cv_file", [None, "", "candidate.txt", "nested/candidate.pdf"])
def test_application_cv_file_rejects_invalid_names(cv_file):
    with pytest.raises(ValueError, match="cvFile"):
        application_cv_file({"cvFile": cv_file})


def test_validate_unique_application_cv_files_returns_output_pairs():
    applications = [
        {"cvFile": "candidate1_1.pdf"},
        {"cvFile": "candidate2_1.pdf"},
    ]

    assert validate_unique_application_cv_files(applications) == [
        ("candidate1_1.pdf", "candidate1_1.json"),
        ("candidate2_1.pdf", "candidate2_1.json"),
    ]


def test_validate_unique_application_cv_files_rejects_case_insensitive_duplicates():
    applications = [
        {"cvFile": "candidate1_1.pdf"},
        {"cvFile": "Candidate1_1.PDF"},
    ]

    with pytest.raises(
        ValueError,
        match=r"Duplicate application cvFile .*applications\[0\].*applications\[1\]",
    ):
        validate_unique_application_cv_files(applications)


def test_run_data_then_pdf_phases_never_renders_before_data_finishes():
    events = []

    run_data_then_pdf_phases(
        ["one", "two"],
        lambda record: events.append(f"data:{record}"),
        lambda record: events.append(f"pdf:{record}"),
        after_data=lambda: events.append("manifest"),
    )

    assert events == [
        "data:one",
        "data:two",
        "manifest",
        "pdf:one",
        "pdf:two",
    ]


def test_render_pdf_atomically_replaces_existing_target(tmp_path):
    target = tmp_path / "candidate.pdf"
    target.write_bytes(b"old-pdf")

    render_pdf_atomically(
        target,
        lambda temporary_path: Path(temporary_path).write_bytes(b"new-pdf"),
    )

    assert target.read_bytes() == b"new-pdf"
    assert list(tmp_path.glob(".*.tmp.pdf")) == []


def test_frontend_job_matches_only_frontend_capable_software_roles():
    roles = find_matching_roles("Middle Frontend Developer - Analytics Dashboard")

    assert roles
    assert {role_base for role_base, _ in roles} == {"Software Engineer"}
    assert {speciality for _, speciality in roles} == {"Frontend", "Full-Stack"}


def test_all_batch_jobs_have_a_supported_matching_role():
    jobs_path = Path(__file__).parents[1] / "scripts" / "jobs.json"
    jobs = json.loads(jobs_path.read_text(encoding="utf-8"))["jobs"]

    unsupported = [job["title"] for job in jobs if not find_matching_roles(job["title"])]

    assert unsupported == []


def test_generic_engineer_title_does_not_bypass_forced_label_validation():
    assert find_matching_roles("Junior Mechanical Engineer") == []

    with pytest.raises(ValueError, match="No supported CV role"):
        require_supported_matching_roles("Junior Mechanical Engineer")
