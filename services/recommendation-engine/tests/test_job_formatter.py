"""Tests for app/services/job_formatter.py (pure text-formatting logic, no I/O)."""

from app.services.job_formatter import (
    _clean_html,
    format_job_as_fields,
    format_job_as_text,
    format_resume_as_fields,
    format_resume_as_text,
)


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


class TestFormatJobAsFields:
    def test_maps_shortDescription_to_jd_overview(self):
        fields = format_job_as_fields({"shortDescription": "<p>Great role</p>"})
        assert fields["jd_overview"] == "Great role"

    def test_maps_benefits_to_jd_preferred(self):
        """Matches app/services/cv_rank_assembly.py's existing benefits -> jd_preferred
        convention -- no job-service schema change, see field_format.py docstring."""
        fields = format_job_as_fields({"benefits": "Remote work, health insurance"})
        assert fields["jd_preferred"] == "Remote work, health insurance"

    def test_maps_requirements_and_responsibilities_directly(self):
        fields = format_job_as_fields({"requirements": "Python required", "responsibilities": "Build APIs"})
        assert fields["jd_requirements"] == "Python required"
        assert fields["jd_responsibilities"] == "Build APIs"

    def test_job_description_text_reuses_format_job_as_text(self):
        job = {"title": "Backend Engineer", "shortDescription": "Great role"}
        fields = format_job_as_fields(job)
        assert fields["job_description_text"] == format_job_as_text(job)

    def test_empty_job_returns_all_empty_fields(self):
        fields = format_job_as_fields({})
        assert fields == {
            "job_description_text": "",
            "jd_overview": "",
            "jd_requirements": "",
            "jd_responsibilities": "",
            "jd_preferred": "",
        }

    def test_returns_exactly_five_job_fields(self):
        fields = format_job_as_fields({"title": "X"})
        assert set(fields.keys()) == {
            "job_description_text", "jd_overview", "jd_requirements", "jd_responsibilities", "jd_preferred",
        }


class TestFormatResumeAsFields:
    def test_structured_keys_used_directly(self):
        resume = {
            "resume_text": "Full CV",
            "resume_summary": "Backend dev",
            "resume_experience": "5 years",
            "resume_skills": "Python, SQL",
            "resume_education": "BSc CS",
        }
        assert format_resume_as_fields(resume) == resume

    def test_falls_back_to_resume_parser_shape(self):
        parsed = {
            "raw_text": "Full CV text",
            "summary": "Backend dev",
            "experience": "5 years at Acme",
            "skills": ["Python", "SQL"],
            "education": "BSc CS",
        }
        fields = format_resume_as_fields(parsed)
        assert fields["resume_text"] == "Full CV text"
        assert fields["resume_summary"] == "Backend dev"
        assert fields["resume_experience"] == "5 years at Acme"
        assert fields["resume_skills"] == "Python, SQL"
        assert fields["resume_education"] == "BSc CS"

    def test_skills_list_joined_with_comma(self):
        fields = format_resume_as_fields({"skills": ["Python", "SQL", "Docker"]})
        assert fields["resume_skills"] == "Python, SQL, Docker"

    def test_skills_string_passed_through(self):
        fields = format_resume_as_fields({"skills": "Python, SQL"})
        assert fields["resume_skills"] == "Python, SQL"

    def test_empty_resume_returns_all_empty_fields(self):
        fields = format_resume_as_fields({})
        assert fields == {
            "resume_text": "",
            "resume_summary": "",
            "resume_experience": "",
            "resume_skills": "",
            "resume_education": "",
        }

    def test_structured_key_takes_priority_over_legacy_key(self):
        fields = format_resume_as_fields({"resume_summary": "New summary", "summary": "Old summary"})
        assert fields["resume_summary"] == "New summary"


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
