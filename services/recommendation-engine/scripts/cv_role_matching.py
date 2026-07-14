"""Job-title to synthetic-CV role matching rules."""

JOB_ROLES = [
    ("Software Engineer", "Backend"),
    ("Software Engineer", "Frontend"),
    ("Software Engineer", "Full-Stack"),
    ("Data Engineer", ""),
    ("Data Scientist", ""),
    ("Machine Learning Engineer", ""),
    ("DevOps / Platform Engineer", ""),
    ("Site Reliability Engineer", ""),
    ("Cloud Solutions Architect", ""),
    ("QA / Automation Engineer", ""),
    ("Security Engineer", ""),
    ("Mobile Engineer", "Android & iOS"),
    ("AI Research Engineer", ""),
    ("Technical Lead", ""),
    ("Staff Engineer", ""),
    ("Product Manager", ""),
    ("Product Manager", "Growth"),
    ("UX / UI Designer", ""),
    ("Business Analyst", ""),
    ("Project Manager", ""),
    ("Data Analyst", ""),
    ("Digital Marketing Manager", ""),
    ("HR Manager", ""),
    ("Finance Analyst", ""),
    ("Solutions Consultant", ""),
    ("Operations Manager", ""),
    ("Customer Success Manager", ""),
]


_ROLE_KEYWORDS: dict[str, list[str]] = {
    "Software Engineer": [
        "software engineer", "backend", "back-end", "back end", "frontend",
        "front-end", "front end", "fullstack", "full-stack", "full stack",
        "web engineer", "application developer", "reactjs", "nextjs",
        "api developer", "service developer", "systems developer",
        "core banking developer", "microservices engineer", "api engineer",
        "spring boot", "embedded services", "digital sales developer",
    ],
    "Data Engineer": ["data engineer", "etl", "data pipeline", "data platform", "data infrastructure"],
    "Data Scientist": ["data scientist", "data science"],
    "Machine Learning Engineer": ["machine learning", "ml engineer", "ai engineer", "deep learning", "llm engineer", "ai intern", "document intelligence", "ai risk"],
    "DevOps / Platform Engineer": ["devops", "platform engineer", "infrastructure engineer", "cloud engineer", "infra engineer", "cloud platform lead", "cloud device engineer"],
    "Site Reliability Engineer": ["sre", "site reliability", "reliability engineer"],
    "Cloud Solutions Architect": ["solutions architect", "cloud architect", "cloud solutions", "enterprise architect"],
    "QA / Automation Engineer": ["qa engineer", "quality assurance", "automation engineer", "sdet", "test engineer", "software tester"],
    "Security Engineer": ["security engineer", "infosec", "cybersecurity", "appsec", "security analyst", "security operations"],
    "Mobile Engineer": ["mobile engineer", "android", "ios", "flutter developer", "react native", "mobile developer", "mobile banking"],
    "AI Research Engineer": ["ai research", "research engineer", "nlp engineer", "computer vision", "research scientist"],
    "Technical Lead": ["tech lead", "technical lead", "engineering lead", "lead engineer", "lead developer"],
    "Staff Engineer": ["staff engineer", "principal engineer", "distinguished engineer"],
    "Product Manager": ["product manager", "product owner", "head of product", "vp of product"],
    "UX / UI Designer": ["ux designer", "ui designer", "product designer", "user experience", "ux/ui", "ui/ux", "interaction designer"],
    "Business Analyst": ["business analyst", "business analysis", "systems analyst", "functional analyst"],
    "Project Manager": ["project manager", "programme manager", "scrum master", "pmo", "delivery manager"],
    "Data Analyst": ["data analyst", "analytics engineer", "business intelligence", "bi analyst", "reporting analyst"],
    "Digital Marketing Manager": ["marketing manager", "digital marketing", "growth manager", "seo specialist", "performance marketing"],
    "HR Manager": ["hr manager", "human resources", "talent acquisition", "people manager", "recruiter", "people & culture"],
    "Finance Analyst": ["finance analyst", "financial analyst", "fp&a", "accounting", "treasury analyst"],
    "Solutions Consultant": ["solutions consultant", "pre-sales", "solutions engineer", "technical consultant", "implementation consultant"],
    "Operations Manager": ["operations manager", "ops manager", "head of operations", "supply chain manager", "operations lead"],
    "Customer Success Manager": ["customer success", "csm", "account manager", "client success", "customer experience"],
}


def _filter_software_specialities(
    job_title: str,
    roles: list[tuple[str, str]],
) -> list[tuple[str, str]]:
    title = job_title.lower()
    allowed: set[str] | None = None

    if any(marker in title for marker in ("fullstack", "full-stack", "full stack")):
        allowed = {"Full-Stack"}
    elif any(marker in title for marker in ("frontend", "front-end", "front end", "reactjs", "nextjs")):
        allowed = {"Frontend", "Full-Stack"}
    elif any(marker in title for marker in ("backend", "back-end", "back end", "api developer", "api engineer", "service developer", "services engineer", "systems developer", "core banking", "microservices", "spring boot")):
        allowed = {"Backend", "Full-Stack"}

    if allowed is None:
        return roles

    return [
        role for role in roles
        if role[0] != "Software Engineer" or role[1] in allowed
    ]


def find_matching_roles(job_title: str) -> list[tuple[str, str]]:
    """Return supported CV roles that genuinely match the supplied job title."""
    title = job_title.lower()
    matched = [
        role for role in JOB_ROLES
        if any(keyword in title for keyword in _ROLE_KEYWORDS.get(role[0], [role[0].lower()]))
    ]
    return _filter_software_specialities(job_title, matched)


def require_supported_matching_roles(job_title: str) -> list[tuple[str, str]]:
    """Return matching roles or reject unsupported forced-label job titles."""
    matched = find_matching_roles(job_title)
    if not matched:
        raise ValueError(
            f"No supported CV role matches forced-label job title: {job_title!r}"
        )
    return matched
