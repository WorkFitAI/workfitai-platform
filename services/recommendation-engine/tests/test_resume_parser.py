"""
Tests for app/services/resume_parser.py (ResumeParser).

pdfplumber.open / PyPDF2.PdfReader are mocked at the boundary — generating a
real PDF byte stream needs a dependency (e.g. reportlab) not in this project's
venv; the parser's own decision logic (fallback chain, skill/experience/
education/contact-info extraction, formatting) is pure-Python and is what's
under test here.
"""

from io import BytesIO
from unittest.mock import MagicMock, patch

import pytest

from app.services.resume_parser import ResumeParser


@pytest.fixture
def parser():
    return ResumeParser()


class TestParseResumeTextExtractionFallback:
    def test_uses_pdfplumber_when_it_succeeds(self, parser):
        with patch.object(parser, "_extract_text_pdfplumber", return_value="A" * 100), \
             patch.object(parser, "_extract_text_pypdf2") as pypdf2_mock:
            result = parser.parse_resume(BytesIO(b"%PDF-1.4"))
        pypdf2_mock.assert_not_called()
        assert result["raw_text"] == "A" * 100

    def test_falls_back_to_pypdf2_when_pdfplumber_text_too_short(self, parser):
        with patch.object(parser, "_extract_text_pdfplumber", return_value="short"), \
             patch.object(parser, "_extract_text_pypdf2", return_value="B" * 100) as pypdf2_mock:
            result = parser.parse_resume(BytesIO(b"%PDF-1.4"))
        pypdf2_mock.assert_called_once()
        assert result["raw_text"] == "B" * 100

    def test_both_extractors_fail_returns_empty_resume(self, parser):
        with patch.object(parser, "_extract_text_pdfplumber", return_value=""), \
             patch.object(parser, "_extract_text_pypdf2", return_value=""):
            result = parser.parse_resume(BytesIO(b"%PDF-1.4"))
        assert result == parser._empty_resume()

    def test_unexpected_exception_returns_empty_resume(self, parser):
        with patch.object(parser, "_extract_text_pdfplumber", side_effect=RuntimeError("boom")):
            result = parser.parse_resume(BytesIO(b"%PDF-1.4"))
        assert result == parser._empty_resume()

    def test_successful_parse_extracts_structured_fields(self, parser):
        text = (
            "Jane Doe\njane@example.com\n(555) 123-4567\n"
            "5 years of experience in Python and React development.\n"
            "Master of Science in Computer Science."
        )
        with patch.object(parser, "_extract_text_pdfplumber", return_value=text):
            result = parser.parse_resume(BytesIO(b"%PDF-1.4"))
        assert "Python" in result["skills"]
        assert "React" in result["skills"]
        assert result["experience_years"] == 5
        assert result["education"] == "Master"
        assert result["contact_info"]["email"] == "jane@example.com"


class TestExtractTextPdfplumber:
    def test_extracts_text_from_all_pages(self, parser):
        page1 = MagicMock()
        page1.extract_text.return_value = "Page one"
        page2 = MagicMock()
        page2.extract_text.return_value = "Page two"
        fake_pdf = MagicMock()
        fake_pdf.pages = [page1, page2]
        fake_pdf.__enter__ = MagicMock(return_value=fake_pdf)
        fake_pdf.__exit__ = MagicMock(return_value=False)

        with patch("app.services.resume_parser.pdfplumber.open", return_value=fake_pdf):
            text = parser._extract_text_pdfplumber(BytesIO(b"%PDF-1.4"))
        assert text == "Page one\nPage two"

    def test_page_with_no_text_is_skipped(self, parser):
        page = MagicMock()
        page.extract_text.return_value = None
        fake_pdf = MagicMock()
        fake_pdf.pages = [page]
        fake_pdf.__enter__ = MagicMock(return_value=fake_pdf)
        fake_pdf.__exit__ = MagicMock(return_value=False)

        with patch("app.services.resume_parser.pdfplumber.open", return_value=fake_pdf):
            text = parser._extract_text_pdfplumber(BytesIO(b"%PDF-1.4"))
        assert text == ""

    def test_exception_returns_empty_string(self, parser):
        with patch("app.services.resume_parser.pdfplumber.open", side_effect=RuntimeError("bad pdf")):
            text = parser._extract_text_pdfplumber(BytesIO(b"%PDF-1.4"))
        assert text == ""


class TestExtractTextPypdf2:
    def test_extracts_text_from_all_pages(self, parser):
        page1 = MagicMock()
        page1.extract_text.return_value = "Page one"
        fake_reader = MagicMock()
        fake_reader.pages = [page1]

        with patch("app.services.resume_parser.PyPDF2.PdfReader", return_value=fake_reader):
            text = parser._extract_text_pypdf2(BytesIO(b"%PDF-1.4"))
        assert text == "Page one"

    def test_exception_returns_empty_string(self, parser):
        with patch("app.services.resume_parser.PyPDF2.PdfReader", side_effect=RuntimeError("bad pdf")):
            text = parser._extract_text_pypdf2(BytesIO(b"%PDF-1.4"))
        assert text == ""


class TestExtractSkills:
    def test_finds_known_skills_case_insensitively(self, parser):
        skills = parser._extract_skills("I know Python, python, and REACT very well.")
        assert skills == ["Python", "React"]

    def test_no_partial_word_matches(self, parser):
        # "go" should not match inside "google" or "going"
        skills = parser._extract_skills("I am going to google things, not programming in Go.")
        assert "Go" in skills

    def test_no_skills_found_returns_empty_list(self, parser):
        assert parser._extract_skills("Nothing relevant here.") == []


class TestExtractExperienceYears:
    @pytest.mark.parametrize("text,expected", [
        ("5 years of experience", 5),
        ("10+ years experience", 10),
        ("experience: 3 years", 3),
        ("2 years in software", 2),
        ("no mention of years", 0),
    ])
    def test_patterns(self, parser, text, expected):
        assert parser._extract_experience_years(text) == expected

    def test_returns_max_across_multiple_matches(self, parser):
        text = "3 years experience, later worked 7 years in industry"
        assert parser._extract_experience_years(text) == 7


class TestExtractEducation:
    @pytest.mark.parametrize("text,expected", [
        ("I hold a Ph.D. in Physics", "Phd"),
        ("Master of Science degree", "Master"),
        ("Bachelor of Arts", "Bachelor"),
        ("Associate degree in IT", "Associate"),
        ("Completed a diploma program", "Diploma"),
        ("No education mentioned", "Unknown"),
    ])
    def test_patterns(self, parser, text, expected):
        assert parser._extract_education(text) == expected

    def test_priority_order_highest_level_wins_by_first_match(self, parser):
        # Both phd and bachelor keywords present — phd checked first in priority list.
        assert parser._extract_education("bachelor and phd degrees") == "Phd"


class TestExtractContactInfo:
    def test_extracts_email_and_phone(self, parser):
        info = parser._extract_contact_info("Contact: jane.doe@example.com or +1-555-123-4567")
        assert info["email"] == "jane.doe@example.com"
        assert "phone" in info

    def test_missing_contact_info_returns_empty_dict(self, parser):
        assert parser._extract_contact_info("No contact details here") == {}


class TestFormatResumeForMatching:
    def test_includes_all_present_sections(self, parser):
        parsed = {
            "skills": ["Python", "SQL"],
            "experience_years": 5,
            "education": "Bachelor",
            "raw_text": "Full resume text",
        }
        text = parser.format_resume_for_matching(parsed)
        assert "Skills: Python, SQL" in text
        assert "Experience: 5 years" in text
        assert "Education: Bachelor" in text
        assert "Full resume text" in text

    def test_zero_experience_years_omitted(self, parser):
        parsed = {"skills": [], "experience_years": 0, "education": "Unknown", "raw_text": ""}
        assert parser.format_resume_for_matching(parsed) == ""

    def test_truncates_long_raw_text(self, parser):
        parsed = {"skills": [], "experience_years": 0, "education": "Unknown", "raw_text": "x" * 2000}
        text = parser.format_resume_for_matching(parsed)
        assert len(text.strip()) == 1000


class TestEmptyResume:
    def test_shape(self, parser):
        empty = parser._empty_resume()
        assert empty == {
            "raw_text": "", "skills": [], "experience_years": 0,
            "education": "Unknown", "contact_info": {},
        }
