"""
CV section extraction via Ollama Cloud.

Ports the RESUME-side logic of workfitai-nckh/runners/extract_sections.py
(SectionExtractor): calls Ollama Cloud (gpt-oss:120b), extracts the 4 resume
sections (summary/experience/skills/education) as JSON, and rotates through
multiple API keys on rate-limit responses (429/409) to stay within quota.

Best-effort by contract: any failure (rate-limited on all keys, malformed JSON,
network error) returns the 4 fields as empty strings rather than raising, so the
caller can keep whatever heuristic-parsed sections it already has.
"""

import json
import logging
import time
from typing import Dict, List, Optional

from ollama import Client

logger = logging.getLogger(__name__)

# Extract only the RESUME sections — the multi-field bi-encoder / cross-encoder
# consume resume_summary/experience/skills/education (see app/services/field_format.py).
RESUME_SECTION_KEYS = ["summary", "experience", "skills", "education"]

# Per-section character cap. NCKH caps at <512 e5 tokens; we use a plain char cap
# (~2000 chars ≈ 500 tokens) to avoid loading a tokenizer model just for truncation.
_MAX_SECTION_CHARS = 2000

# Input document cap — mirrors NCKH's text[:10000] to stay within model context.
_MAX_INPUT_CHARS = 10000

# Overall wall-clock budget for one extraction. cv-service's client blocks with a
# ~60s timeout and gives up after that, so there's no point the server grinding
# longer (it just burns Ollama quota + ties up the anyio threadpool). Once this
# budget is exceeded we bail with empty sections (best-effort).
_MAX_TOTAL_SECONDS = 45

# Cooldown when every key is rate-limited. Kept short (was 30s in NCKH's batch
# runner) so a single request can't blow the total budget on one sleep.
_ALL_KEYS_COOLDOWN_SECONDS = 5

SECTION_EXTRACTION_PROMPT = """You are a document parser. Extract structured sections from the following document.

Document Type: RESUME

For RESUME, extract these sections:
1. summary: Professional summary, objective, or profile statement
2. experience: PAID work history only — actual jobs, internships, or employment
   the candidate held (employer name, job title, dates, responsibilities performed
   as an employee). Do NOT include personal, academic, side, or portfolio projects
   here, even if the document lists them under a combined "Experience & Projects"
   section — leave those out of this field entirely.
3. skills: Technical skills, competencies, tools, technologies
4. education: Degrees, certifications, courses, training

IMPORTANT RULES:
- If a section is not present in the document, return empty string ""
- Preserve key information, remove excessive formatting/whitespace
- Each section must be < 512 tokens (roughly < 2000 characters)
- If a section is too long, keep the most important/relevant information
- Do NOT add information not present in the original document
- Do NOT use markdown formatting in the output
- In "experience", keep the candidate's own stated job title/level exactly as
  written (e.g. "Intern", "Fresher", "Junior") — never upgrade or infer a more
  senior title than what the document actually states

Return ONLY a valid JSON object with this exact structure (no markdown code blocks):
{{
  "summary": "...",
  "experience": "...",
  "skills": "...",
  "education": "..."
}}

Document Text:
{document_text}"""


def _empty_sections() -> Dict[str, str]:
    return {key: "" for key in RESUME_SECTION_KEYS}


class CvSectionExtractor:
    """Extract resume sections from raw CV text using Ollama Cloud with key rotation."""

    def __init__(self, api_keys: List[str], model: str = "gpt-oss:120b"):
        if not api_keys:
            raise ValueError("At least one Ollama API key is required")
        self.api_keys = api_keys
        self.current_key_idx = 0
        self.model = model
        logger.info(
            "CvSectionExtractor initialised with %d API key(s), model=%s",
            len(api_keys), model,
        )

    def _get_client(self) -> Client:
        api_key = self.api_keys[self.current_key_idx]
        return Client(host="https://ollama.com", headers={"Authorization": f"Bearer {api_key}"})

    def _rotate_api_key(self) -> None:
        self.current_key_idx = (self.current_key_idx + 1) % len(self.api_keys)
        logger.info("Rotated to Ollama API key %d/%d", self.current_key_idx + 1, len(self.api_keys))

    def extract_resume_sections(self, text: str, max_retries: int = 3) -> Dict[str, str]:
        """
        Extract {summary, experience, skills, education} from resume text.

        Returns all-empty-strings on any unrecoverable failure (never raises), so
        the caller keeps its existing heuristic sections.
        """
        if not text or not text.strip():
            return _empty_sections()

        prompt = SECTION_EXTRACTION_PROMPT.format(document_text=text[:_MAX_INPUT_CHARS])
        messages = [{"role": "user", "content": prompt}]

        keys_tried = set()
        attempt = 0
        max_attempts = max_retries * len(self.api_keys)
        deadline = time.monotonic() + _MAX_TOTAL_SECONDS

        while attempt < max_attempts:
            if time.monotonic() >= deadline:
                logger.warning("Ollama extraction exceeded %ds budget — returning empty", _MAX_TOTAL_SECONDS)
                return _empty_sections()
            try:
                client = self._get_client()
                response = client.chat(self.model, messages=messages, stream=False)
                content = response["message"]["content"].strip()
                content = self._strip_markdown_fences(content)
                sections = json.loads(content)
                return self._normalize(sections)

            except json.JSONDecodeError as e:
                logger.warning("Ollama JSON parse error (attempt %d/%d): %s", attempt + 1, max_attempts, e)
                if attempt >= max_attempts - 1:
                    return _empty_sections()
                time.sleep(2 ** (attempt % 3))
                attempt += 1

            except Exception as e:
                error_str = str(e).lower()
                is_rate_limit = (
                    "429" in error_str
                    or "409" in error_str
                    or "rate limit" in error_str
                    or "too many requests" in error_str
                )
                if is_rate_limit and len(self.api_keys) > 1:
                    keys_tried.add(self.current_key_idx)
                    if len(keys_tried) >= len(self.api_keys):
                        logger.warning(
                            "Ollama rate-limited on all %d keys, waiting %ds...",
                            len(self.api_keys), _ALL_KEYS_COOLDOWN_SECONDS,
                        )
                        time.sleep(_ALL_KEYS_COOLDOWN_SECONDS)
                        keys_tried.clear()
                    self._rotate_api_key()
                    time.sleep(2)
                else:
                    logger.warning("Ollama API error (attempt %d/%d): %s", attempt + 1, max_attempts, e)
                    if attempt >= max_attempts - 1:
                        return _empty_sections()
                    time.sleep(2 ** (attempt % 3))
                attempt += 1

        return _empty_sections()

    @staticmethod
    def _strip_markdown_fences(content: str) -> str:
        """Remove ```json / ``` fences the model sometimes wraps output in."""
        if content.startswith("```json"):
            content = content[7:]
        elif content.startswith("```"):
            content = content[3:]
        if content.endswith("```"):
            content = content[:-3]
        return content.strip()

    @staticmethod
    def _normalize(sections: Dict) -> Dict[str, str]:
        """Coerce to the 4 known keys, stringify, and char-cap each value."""
        result = _empty_sections()
        for key in RESUME_SECTION_KEYS:
            value = sections.get(key, "")
            if value:
                text = str(value).strip()
                if len(text) > _MAX_SECTION_CHARS:
                    text = text[:_MAX_SECTION_CHARS] + "..."
                result[key] = text
        return result


# Module-level lazy singleton -------------------------------------------------

_extractor: Optional[CvSectionExtractor] = None
_extractor_initialised = False


def get_cv_section_extractor() -> Optional[CvSectionExtractor]:
    """
    Return the shared extractor, or None when the feature is disabled or no API
    keys are configured (caller then keeps heuristic sections). Built once.
    """
    global _extractor, _extractor_initialised
    if _extractor_initialised:
        return _extractor

    _extractor_initialised = True
    from app.config import get_settings

    settings = get_settings()
    if not settings.CV_OLLAMA_EXTRACTION_ENABLED:
        logger.info("CV Ollama extraction disabled (CV_OLLAMA_EXTRACTION_ENABLED=false)")
        _extractor = None
        return None

    api_keys = settings.get_ollama_api_keys()
    if not api_keys:
        logger.warning("CV Ollama extraction enabled but no OLLAMA_API_KEYS configured — disabling")
        _extractor = None
        return None

    _extractor = CvSectionExtractor(api_keys=api_keys, model=settings.OLLAMA_MODEL)
    return _extractor
