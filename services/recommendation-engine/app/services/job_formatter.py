"""
Job data formatter - convert PostgreSQL job objects to text format
TODO: Implementation in next phase
"""

import logging
from typing import Dict

logger = logging.getLogger(__name__)


def format_job_as_text(job_data: Dict) -> str:
    """
    Convert job object from PostgreSQL to text format
    matching the training data format
    
    TODO: Complete implementation
    """
    # Extract fields
    title = job_data.get('title', '')
    description = job_data.get('description', '')
    
    # Placeholder formatting
    job_text = f"""Title: {title}

Description:
{description}
"""
    
    logger.debug("TODO: Complete job formatting implementation")
    return job_text.strip()
