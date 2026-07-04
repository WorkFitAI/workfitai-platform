"""Tests for app/services/job_formatter.py (pure text-formatting logic, no I/O)."""

from app.services.job_formatter import _clean_html, format_job_as_text, format_resume_as_text


class TestFormatJobAsText:
    def test_full_job_includes_all_sections(self):
        job = {
            "title": "Backend Engineer",
            "company": {"companyName": "Acme", "companySize": "50-200"},
            "location": "Remote",
            "employmentType": "FULL_TIME",
            "experienceLevel": "senior",
            "requiredExperience": "5+ years",
            "educationLevel": "Bachelor",
            "salaryMin": 90000,
            "salaryMax": 120000,
            "currency": "USD",
            "skills": ["Python", "FastAPI"],
            "shortDescription": "<p>Great role</p>",
            "description": "<b>Full</b> description",
            "requirements": "Python required",
            "responsibilities": "Build APIs",
            "benefits": "Remote work",
        }
        text = format_job_as_text(job)
        assert "Job Title: Backend Engineer" in text
        assert "Company: Acme" in text
        assert "Company Size: 50-200" in text
        assert "Location: Remote" in text
        assert "Type: Full Time" in text
        assert "Level: Senior" in text
        assert "Required: 5+ years" in text
        assert "Education: Bachelor" in text
        assert "Salary: 90,000 - 120,000 USD" in text
        assert "Required Skills: Python, FastAPI" in text
        assert "Summary: Great role" in text
        assert "Description: Full description" in text
        assert "Requirements: Python required" in text
        assert "Responsibilities: Build APIs" in text
        assert "Benefits: Remote work" in text

    def test_minimal_job_with_only_title(self):
        text = format_job_as_text({"title": "QA Engineer"})
        assert text == "Job Title: QA Engineer"

    def test_empty_job_returns_empty_string(self):
        assert format_job_as_text({}) == ""

    def test_salary_min_only(self):
        text = format_job_as_text({"salaryMin": 50000})
        assert "Salary: From 50,000 USD" in text

    def test_salary_max_only(self):
        text = format_job_as_text({"salaryMax": 80000})
        assert "Salary: Up to 80,000 USD" in text

    def test_location_only_no_employment_type(self):
        text = format_job_as_text({"location": "NYC"})
        assert "Location: NYC" in text

    def test_employment_type_only_no_location(self):
        text = format_job_as_text({"employmentType": "PART_TIME"})
        assert "Type: Part Time" in text


class TestCleanHtml:
    def test_removes_tags(self):
        assert _clean_html("<p>Hello <b>World</b></p>") == "Hello World"

    def test_decodes_entities(self):
        assert _clean_html("Fish &amp; Chips") == "Fish & Chips"

    def test_collapses_whitespace(self):
        assert _clean_html("a   b\n\nc") == "a b c"

    def test_empty_string_returns_empty(self):
        assert _clean_html("") == ""

    def test_none_returns_empty(self):
        assert _clean_html(None) == ""


class TestFormatResumeAsText:
    def test_all_sections_present(self):
        resume = {
            "summary": "Backend dev",
            "skills": ["Python", "SQL"],
            "experience": "5 years at Acme",
            "education": "BSc CS",
        }
        text = format_resume_as_text(resume)
        assert "Professional Summary: Backend dev" in text
        assert "Skills: Python, SQL" in text
        assert "Experience: 5 years at Acme" in text
        assert "Education: BSc CS" in text

    def test_skills_as_string_not_list(self):
        text = format_resume_as_text({"skills": "Python, SQL"})
        assert "Skills: Python, SQL" in text

    def test_missing_sections_omitted(self):
        text = format_resume_as_text({"summary": "Just a summary"})
        assert text == "Professional Summary: Just a summary"

    def test_empty_resume_returns_empty_string(self):
        assert format_resume_as_text({}) == ""
