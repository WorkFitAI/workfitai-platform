"""
Job data formatter - convert job data to text format for embeddings
"""

import logging
import re
import html
from typing import Dict

logger = logging.getLogger(__name__)


def format_job_as_text(job_data: Dict) -> str:
    """
    Convert job object to formatted text suitable for E5-Large embeddings
    Format follows natural language structure for better semantic matching
    
    Args:
        job_data: Job data dict from Kafka event
        
    Returns:
        Formatted text representation of the job
    """
    sections = []
    
    # Title
    title = job_data.get("title", "")
    if title:
        sections.append(f"Job Title: {title}")
    
    # Company
    company = job_data.get("company", {})
    if company:
        company_name = company.get("companyName", "")
        if company_name:
            sections.append(f"Company: {company_name}")
        
        company_size = company.get("companySize")
        if company_size:
            sections.append(f"Company Size: {company_size}")
    
    # Location and Employment Type
    location = job_data.get("location", "")
    employment_type = job_data.get("employmentType", "")
    if location or employment_type:
        location_text = f"Location: {location}" if location else ""
        emp_type_text = f"Type: {employment_type.replace('_', ' ').title()}" if employment_type else ""
        sections.append(f"{location_text}. {emp_type_text}".strip(". "))
    
    # Experience and Education
    experience_level = job_data.get("experienceLevel", "")
    required_exp = job_data.get("requiredExperience", "")
    education = job_data.get("educationLevel", "")
    
    exp_parts = []
    if experience_level:
        exp_parts.append(f"Level: {experience_level.title()}")
    if required_exp:
        exp_parts.append(f"Required: {required_exp}")
    if education:
        exp_parts.append(f"Education: {education}")
    
    if exp_parts:
        sections.append(". ".join(exp_parts))
    
    # Salary
    salary_min = job_data.get("salaryMin")
    salary_max = job_data.get("salaryMax")
    currency = job_data.get("currency", "USD")
    
    if salary_min is not None and salary_max is not None:
        sections.append(f"Salary: {salary_min:,.0f} - {salary_max:,.0f} {currency}")
    elif salary_min is not None:
        sections.append(f"Salary: From {salary_min:,.0f} {currency}")
    elif salary_max is not None:
        sections.append(f"Salary: Up to {salary_max:,.0f} {currency}")
    
    # Skills
    skills = job_data.get("skills", [])
    if skills:
        skills_text = ", ".join(skills)
        sections.append(f"Required Skills: {skills_text}")
    
    # Short Description
    short_desc = job_data.get("shortDescription", "")
    if short_desc:
        sections.append(f"\nSummary: {_clean_html(short_desc)}")
    
    # Full Description
    description = job_data.get("description", "")
    if description:
        sections.append(f"\nDescription: {_clean_html(description)}")
    
    # Requirements
    requirements = job_data.get("requirements", "")
    if requirements:
        sections.append(f"\nRequirements: {_clean_html(requirements)}")
    
    # Responsibilities
    responsibilities = job_data.get("responsibilities", "")
    if responsibilities:
        sections.append(f"\nResponsibilities: {_clean_html(responsibilities)}")
    
    # Benefits
    benefits = job_data.get("benefits", "")
    if benefits:
        sections.append(f"\nBenefits: {_clean_html(benefits)}")
    
    # Combine all sections
    formatted_text = "\n".join(sections)
    
    logger.debug(f"Formatted job text ({len(formatted_text)} chars)")
    return formatted_text


def _clean_html(text: str) -> str:
    """
    Remove HTML tags and clean up text
    
    Args:
        text: Text that may contain HTML
        
    Returns:
        Cleaned text
    """
    if not text:
        return ""
    
    # Remove HTML tags
    text = re.sub(r'<[^>]+>', '', text)
    
    # Decode HTML entities
    text = html.unescape(text)
    
    # Clean up whitespace
    text = re.sub(r'\s+', ' ', text)
    text = text.strip()
    
    return text


def format_resume_as_text(resume_data: Dict) -> str:
    """
    Format resume/CV data as text for matching
    
    Args:
        resume_data: Resume data dict
        
    Returns:
        Formatted text representation
    """
    sections = []
    
    # Extract key sections from resume
    if "summary" in resume_data:
        sections.append(f"Professional Summary: {resume_data['summary']}")
    
    if "skills" in resume_data:
        skills = resume_data["skills"]
        if isinstance(skills, list):
            sections.append(f"Skills: {', '.join(skills)}")
        else:
            sections.append(f"Skills: {skills}")
    
    if "experience" in resume_data:
        sections.append(f"Experience: {resume_data['experience']}")
    
    if "education" in resume_data:
        sections.append(f"Education: {resume_data['education']}")
    
    return "\n".join(sections)
    return job_text.strip()
