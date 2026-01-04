"""
Resume parser for extracting information from PDF files
"""

import logging
import re
from typing import Dict, List, Optional, BinaryIO
import PyPDF2
import pdfplumber

logger = logging.getLogger(__name__)


class ResumeParser:
    """
    Extract structured information from resume PDFs
    Uses PyPDF2 as fallback and pdfplumber for better text extraction
    """
    
    def __init__(self):
        """Initialize parser"""
        logger.info("✓ ResumeParser initialized")
        
        # Common technical skills patterns
        self.skill_keywords = {
            'programming': ['python', 'java', 'javascript', 'c++', 'c#', 'ruby', 'php', 'swift', 'kotlin', 'go', 'rust'],
            'web': ['react', 'angular', 'vue', 'node.js', 'django', 'flask', 'spring', 'express'],
            'database': ['mysql', 'postgresql', 'mongodb', 'redis', 'oracle', 'sql server', 'cassandra'],
            'cloud': ['aws', 'azure', 'gcp', 'docker', 'kubernetes', 'terraform'],
            'tools': ['git', 'jenkins', 'jira', 'confluence', 'tableau']
        }
    
    def parse_resume(self, pdf_file: BinaryIO) -> Dict:
        """
        Extract text and structure from PDF resume
        
        Args:
            pdf_file: Binary file object of PDF
            
        Returns:
            Parsed resume data dict
        """
        try:
            # Try pdfplumber first (better quality)
            raw_text = self._extract_text_pdfplumber(pdf_file)
            
            # Fallback to PyPDF2 if pdfplumber fails
            if not raw_text or len(raw_text) < 50:
                pdf_file.seek(0)  # Reset file pointer
                raw_text = self._extract_text_pypdf2(pdf_file)
            
            if not raw_text:
                logger.warning("Failed to extract text from PDF")
                return self._empty_resume()
            
            logger.info(f"Extracted {len(raw_text)} characters from resume")
            
            # Extract structured information
            skills = self._extract_skills(raw_text)
            experience_years = self._extract_experience_years(raw_text)
            education = self._extract_education(raw_text)
            contact_info = self._extract_contact_info(raw_text)
            
            return {
                'raw_text': raw_text,
                'skills': skills,
                'experience_years': experience_years,
                'education': education,
                'contact_info': contact_info
            }
            
        except Exception as e:
            logger.error(f"Error parsing resume: {e}", exc_info=True)
            return self._empty_resume()
    
    def _extract_text_pdfplumber(self, pdf_file: BinaryIO) -> str:
        """Extract text using pdfplumber (better quality)"""
        try:
            text_parts = []
            with pdfplumber.open(pdf_file) as pdf:
                for page in pdf.pages:
                    text = page.extract_text()
                    if text:
                        text_parts.append(text)
            
            return '\n'.join(text_parts)
        except Exception as e:
            logger.warning(f"pdfplumber extraction failed: {e}")
            return ""
    
    def _extract_text_pypdf2(self, pdf_file: BinaryIO) -> str:
        """Extract text using PyPDF2 (fallback)"""
        try:
            text_parts = []
            pdf_reader = PyPDF2.PdfReader(pdf_file)
            
            for page in pdf_reader.pages:
                text = page.extract_text()
                if text:
                    text_parts.append(text)
            
            return '\n'.join(text_parts)
        except Exception as e:
            logger.error(f"PyPDF2 extraction failed: {e}")
            return ""
    
    def _extract_skills(self, text: str) -> List[str]:
        """
        Extract technical skills from resume
        
        Args:
            text: Resume text
            
        Returns:
            List of identified skills
        """
        text_lower = text.lower()
        found_skills = []
        
        # Search for all skill keywords
        for category, skills in self.skill_keywords.items():
            for skill in skills:
                # Use word boundaries to avoid partial matches
                pattern = r'\b' + re.escape(skill) + r'\b'
                if re.search(pattern, text_lower):
                    found_skills.append(skill.title())
        
        # Remove duplicates while preserving order
        seen = set()
        unique_skills = []
        for skill in found_skills:
            if skill.lower() not in seen:
                seen.add(skill.lower())
                unique_skills.append(skill)
        
        logger.debug(f"Extracted {len(unique_skills)} skills")
        return unique_skills
    
    def _extract_experience_years(self, text: str) -> int:
        """
        Extract total years of experience
        
        Args:
            text: Resume text
            
        Returns:
            Years of experience (0 if not found)
        """
        # Look for patterns like "5 years experience", "5+ years", etc.
        patterns = [
            r'(\d+)\+?\s*years?\s+(?:of\s+)?experience',
            r'experience\s*:\s*(\d+)\+?\s*years?',
            r'(\d+)\+?\s*years?\s+in'
        ]
        
        max_years = 0
        for pattern in patterns:
            matches = re.findall(pattern, text.lower())
            for match in matches:
                years = int(match)
                if years > max_years:
                    max_years = years
        
        logger.debug(f"Extracted experience: {max_years} years")
        return max_years
    
    def _extract_education(self, text: str) -> str:
        """
        Extract highest education level
        
        Args:
            text: Resume text
            
        Returns:
            Education level string
        """
        text_lower = text.lower()
        
        # Education levels in order of priority
        education_levels = [
            ('phd', ['ph.d', 'phd', 'doctorate', 'doctoral']),
            ('master', ['master', 'm.s', 'msc', 'm.sc', 'mba']),
            ('bachelor', ['bachelor', 'b.s', 'bsc', 'b.sc', 'b.a', 'b.e', 'b.tech']),
            ('associate', ['associate', 'a.s', 'a.a']),
            ('diploma', ['diploma', 'certificate'])
        ]
        
        for level, keywords in education_levels:
            for keyword in keywords:
                if keyword in text_lower:
                    logger.debug(f"Extracted education: {level}")
                    return level.title()
        
        logger.debug("Education level not found")
        return "Unknown"
    
    def _extract_contact_info(self, text: str) -> Dict:
        """
        Extract contact information
        
        Args:
            text: Resume text
            
        Returns:
            Dict with email, phone, etc.
        """
        contact_info = {}
        
        # Extract email
        email_pattern = r'\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b'
        emails = re.findall(email_pattern, text)
        if emails:
            contact_info['email'] = emails[0]
        
        # Extract phone (various formats)
        phone_pattern = r'[\+\(]?[1-9][0-9 .\-\(\)]{8,}[0-9]'
        phones = re.findall(phone_pattern, text)
        if phones:
            contact_info['phone'] = phones[0]
        
        return contact_info
    
    def _empty_resume(self) -> Dict:
        """Return empty resume structure"""
        return {
            'raw_text': '',
            'skills': [],
            'experience_years': 0,
            'education': 'Unknown',
            'contact_info': {}
        }
    
    def format_resume_for_matching(self, parsed_resume: Dict) -> str:
        """
        Format parsed resume for embedding generation
        
        Args:
            parsed_resume: Parsed resume dict
            
        Returns:
            Formatted text for embedding
        """
        sections = []
        
        # Skills
        skills = parsed_resume.get('skills', [])
        if skills:
            sections.append(f"Skills: {', '.join(skills)}")
        
        # Experience
        experience_years = parsed_resume.get('experience_years', 0)
        if experience_years > 0:
            sections.append(f"Experience: {experience_years} years")
        
        # Education
        education = parsed_resume.get('education', 'Unknown')
        if education != 'Unknown':
            sections.append(f"Education: {education}")
        
        # Raw text (truncated if too long)
        raw_text = parsed_resume.get('raw_text', '')
        if raw_text:
            # Take first 1000 chars to avoid too long embeddings
            text_sample = raw_text[:1000] if len(raw_text) > 1000 else raw_text
            sections.append(f"\n{text_sample}")
        
        formatted_text = "\n".join(sections)
        logger.debug(f"Formatted resume to {len(formatted_text)} chars")
        
        return formatted_text
        # TODO: Implement formatting
        return ""
