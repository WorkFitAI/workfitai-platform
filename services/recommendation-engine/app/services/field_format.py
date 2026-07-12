"""
Shared field definitions and text-formatting for the multi-field
job-recommend bi-encoder / cross-encoder (bi-encoder-e5-large-multifield,
cross-encoder-structured).

Mirrors job-recomendation/source/data_utils.py's RESUME_FIELDS/JOB_FIELDS and
build_fallback_text() exactly (field order, section headers, missing-field
marker) so text built here matches what those models were trained on.
"""

from typing import Dict, Iterable, List, Optional

# Raw full-text field first, then structured sub-fields — order matters, it's
# the header/iteration order used by build_fallback_text() and by
# EmbeddingGenerator's per-field encode/pool methods.
RESUME_FIELDS: List[str] = [
    "resume_text",
    "resume_summary",
    "resume_experience",
    "resume_skills",
    "resume_education",
]
JOB_FIELDS: List[str] = [
    "job_description_text",
    "jd_overview",
    "jd_requirements",
    "jd_responsibilities",
    "jd_preferred",
]

_FIELD_HEADERS: Dict[str, str] = {
    "resume_text": "FULL TEXT",
    "resume_summary": "SUMMARY",
    "resume_experience": "EXPERIENCE",
    "resume_skills": "SKILLS",
    "resume_education": "EDUCATION",
    "job_description_text": "FULL TEXT",
    "jd_overview": "OVERVIEW",
    "jd_requirements": "REQUIREMENTS",
    "jd_responsibilities": "RESPONSIBILITIES",
    "jd_preferred": "PREFERRED",
}


def field_present(text: Optional[str]) -> bool:
    """True if a text value is non-empty (not None, not blank, not the
    literal string "nan")."""
    if text is None:
        return False
    text = str(text).strip()
    return bool(text) and text.lower() != "nan"


def build_fallback_text(fields: Dict[str, Optional[str]], field_order: Iterable[str]) -> str:
    """Concatenate present fields under labeled headers; explicitly mark
    missing ones (e.g. "[EDUCATION: MISSING]") instead of silently omitting
    them, so the resulting string is itself auditable and matches exactly
    what the cross-encoder was trained on."""
    parts: List[str] = []
    for name in field_order:
        header = _FIELD_HEADERS.get(name, name.upper())
        value = fields.get(name)
        if field_present(value):
            parts.append(f"[{header}]\n{str(value).strip()}")
        else:
            parts.append(f"[{header}: MISSING]")
    return "\n\n".join(parts)
