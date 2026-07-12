"""
Internal-only route for CV section extraction via Ollama Cloud.

Not routed through API Gateway — accessible only within the Docker network.
cv-service POSTs the raw PDF text of a freshly uploaded CV here (on a background
thread, after returning its own response) and gets back the 4 resume sections
extracted by Ollama, which it then writes over its heuristic-parsed sections.

Best-effort by contract: when extraction is disabled, unconfigured, or fails,
the endpoint returns 200 with extracted=false and empty fields so the caller
simply keeps its existing sections instead of retrying/erroring.
"""

import logging

import anyio
from fastapi import APIRouter
from pydantic import BaseModel

from app.services.cv_section_extractor import get_cv_section_extractor

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/internal/cv", tags=["CV Section Extraction"])


class ExtractSectionsRequest(BaseModel):
    text: str
    doc_type: str = "RESUME"


class ExtractSectionsResponse(BaseModel):
    extracted: bool = False
    summary: str = ""
    experience: str = ""
    skills: str = ""
    education: str = ""


@router.post("/extract-sections", response_model=ExtractSectionsResponse)
async def extract_sections(request: ExtractSectionsRequest) -> ExtractSectionsResponse:
    extractor = get_cv_section_extractor()
    if extractor is None or not request.text or not request.text.strip():
        return ExtractSectionsResponse(extracted=False)

    try:
        # Ollama call is synchronous and slow — run it off the event loop.
        sections = await anyio.to_thread.run_sync(extractor.extract_resume_sections, request.text)
    except Exception as exc:  # never surface a 500 into a caller retry storm
        logger.error("CV section extraction failed unexpectedly: %s", exc, exc_info=True)
        return ExtractSectionsResponse(extracted=False)

    # All-empty result means Ollama failed internally (best-effort) — signal the
    # caller to keep its heuristic sections rather than overwrite with blanks.
    if not any(sections.values()):
        return ExtractSectionsResponse(extracted=False)

    return ExtractSectionsResponse(
        extracted=True,
        summary=sections.get("summary", ""),
        experience=sections.get("experience", ""),
        skills=sections.get("skills", ""),
        education=sections.get("education", ""),
    )
