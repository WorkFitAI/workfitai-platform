"""Output naming rules shared by CV dataset generation and its tests."""

from pathlib import Path


def application_cv_file(app: dict) -> tuple[str, str]:
    """Return the application-owned PDF filename and companion JSON filename."""
    raw_cv_file = app.get("cvFile")
    if not isinstance(raw_cv_file, str) or not raw_cv_file.strip():
        raise ValueError("Application cvFile must be a non-empty PDF filename")

    cv_file = Path(raw_cv_file.strip())
    if cv_file.name != raw_cv_file.strip() or cv_file.suffix.lower() != ".pdf":
        raise ValueError(
            f"Application cvFile must be a plain PDF filename, got: {raw_cv_file!r}"
        )

    return cv_file.name, f"{cv_file.stem}.json"


def validate_unique_application_cv_files(
    applications: list[dict],
) -> list[tuple[str, str]]:
    """Validate application-owned output names before batch generation starts."""
    output_files: list[tuple[str, str]] = []
    seen: dict[str, int] = {}

    for position, app in enumerate(applications):
        pdf_file, json_file = application_cv_file(app)
        normalized = pdf_file.casefold()
        previous_position = seen.get(normalized)
        if previous_position is not None:
            raise ValueError(
                "Duplicate application cvFile "
                f"{pdf_file!r} at applications[{previous_position}] and "
                f"applications[{position}]"
            )

        seen[normalized] = position
        output_files.append((pdf_file, json_file))

    return output_files
