#!/usr/bin/env python3
"""
generate_cv_pdfs.py
───────────────────
Generate synthetic CV PDF files for testing the WorkFitAI cv-refer model.

Template style: Harvard / Oxford single-column professional CV.
  ─ Full name as large header
  ─ Contact bar below name
  ─ Horizontal rule separators for each section
  ─ Date in a fixed left column, details on the right  (experience / education)
  ─ No photo

Each PDF is accompanied by a <basename>.json with the 4 model fields:
    • resume_summary    ≤ 400 chars
    • resume_experience ≤ 512 chars
    • resume_skills     ≤ 300 chars
    • resume_education  ≤ 256 chars

A manifest.json aggregates all records.

Usage
-----
    python generate_cv_pdfs.py [--count N] [--output DIR] [--seed SEED]

Dependencies
------------
    pip install faker reportlab
"""

import argparse
import csv as _csv_module
import json
import os
import re
import random
import sys
from datetime import date, timedelta
from pathlib import Path

try:
    from faker import Faker
    from reportlab.lib import colors
    from reportlab.lib.enums import TA_CENTER, TA_LEFT, TA_RIGHT, TA_JUSTIFY
    from reportlab.lib.pagesizes import A4
    from reportlab.lib.styles import ParagraphStyle
    from reportlab.lib.units import mm
    from reportlab.platypus import (
        BaseDocTemplate,
        Frame,
        HRFlowable,
        PageTemplate,
        Paragraph,
        Spacer,
        Table,
        TableStyle,
        KeepTogether,
    )
    from reportlab.lib.colors import HexColor, black, white
except ImportError:
    print("Missing deps. Run:  pip install faker reportlab")
    sys.exit(1)

# pdfplumber is only needed for --extract mode; import lazily
try:
    import pdfplumber as _pdfplumber
except ImportError:
    _pdfplumber = None  # will error gracefully at runtime if --extract is used

# ─────────────────────────────────────────────────────────────────────────────
# PAGE GEOMETRY  (A4, generous margins like Oxford/Harvard printed CVs)
# ─────────────────────────────────────────────────────────────────────────────
PAGE_W, PAGE_H = A4
ML = 22 * mm   # left margin
MR = 22 * mm   # right margin
MT = 22 * mm   # top margin
MB = 20 * mm   # bottom margin
BODY_W = PAGE_W - ML - MR

# Date column width in two-col rows (experience / education)
DATE_COL = 30 * mm
DETAIL_COL = BODY_W - DATE_COL - 4 * mm

# ─────────────────────────────────────────────────────────────────────────────
# COLOUR PALETTE  (muted, professional – Harvard / Oxford feel)
# ─────────────────────────────────────────────────────────────────────────────
C_NAME     = HexColor("#1A1A2E")   # almost-black navy for name
C_ACCENT   = HexColor("#1A3A5C")   # Oxford blue for section headers & rule
C_BODY     = HexColor("#1F2937")   # near-black body text
C_MUTED    = HexColor("#4B5563")   # grey for subtitles / dates
C_RULE     = HexColor("#1A3A5C")   # rule colour
C_CONTACT  = HexColor("#374151")   # contact bar text
C_BULLET   = HexColor("#1A3A5C")   # bullet square colour


# ─────────────────────────────────────────────────────────────────────────────
# STYLES
# ─────────────────────────────────────────────────────────────────────────────
def build_styles() -> dict:
    return {
        "name": ParagraphStyle(
            "Name",
            fontName="Helvetica-Bold",
            fontSize=24,
            textColor=C_NAME,
            leading=29,
            spaceAfter=1,
            alignment=TA_CENTER,
        ),
        "tagline": ParagraphStyle(
            "Tagline",
            fontName="Helvetica",
            fontSize=11,
            textColor=C_MUTED,
            leading=15,
            spaceAfter=3,
            alignment=TA_CENTER,
        ),
        "contact": ParagraphStyle(
            "Contact",
            fontName="Helvetica",
            fontSize=9,
            textColor=C_CONTACT,
            leading=13,
            alignment=TA_CENTER,
            spaceAfter=6,
        ),
        "section": ParagraphStyle(
            "Section",
            fontName="Helvetica-Bold",
            fontSize=10.5,
            textColor=C_ACCENT,
            leading=14,
            spaceBefore=12,
            spaceAfter=3,
            letterSpacing=0.8,
        ),
        "job_title": ParagraphStyle(
            "JobTitle",
            fontName="Helvetica-Bold",
            fontSize=9.5,
            textColor=C_BODY,
            leading=13,
            spaceAfter=1,
        ),
        "job_org": ParagraphStyle(
            "JobOrg",
            fontName="Helvetica-Oblique",
            fontSize=9,
            textColor=C_MUTED,
            leading=12,
            spaceAfter=3,
        ),
        "date": ParagraphStyle(
            "Date",
            fontName="Helvetica",
            fontSize=8.5,
            textColor=C_MUTED,
            leading=12,
            alignment=TA_RIGHT,
        ),
        "body": ParagraphStyle(
            "Body",
            fontName="Helvetica",
            fontSize=9,
            textColor=C_BODY,
            leading=13.5,
            spaceAfter=3,
            alignment=TA_JUSTIFY,
        ),
        "bullet": ParagraphStyle(
            "Bullet",
            fontName="Helvetica",
            fontSize=9,
            textColor=C_BODY,
            leading=13.5,
            leftIndent=10,
            firstLineIndent=0,
            spaceAfter=2,
            alignment=TA_JUSTIFY,
        ),
        "skills_label": ParagraphStyle(
            "SkillsLabel",
            fontName="Helvetica-Bold",
            fontSize=9,
            textColor=C_BODY,
            leading=13,
            spaceAfter=1,
        ),
        "skills_val": ParagraphStyle(
            "SkillsVal",
            fontName="Helvetica",
            fontSize=9,
            textColor=C_MUTED,
            leading=13,
            spaceAfter=5,
        ),
        "field_label": ParagraphStyle(
            "FieldLabel",
            fontName="Helvetica-Bold",
            fontSize=7,
            textColor=HexColor("#9333EA"),
            leading=9,
            spaceAfter=2,
        ),
        "small_muted": ParagraphStyle(
            "SmallMuted",
            fontName="Helvetica",
            fontSize=8,
            textColor=C_MUTED,
            leading=11,
            spaceAfter=2,
        ),
    }


# ─────────────────────────────────────────────────────────────────────────────
# DATA CORPUS
# ─────────────────────────────────────────────────────────────────────────────
SKILLS_BY_CAT = {
    # ── Engineering ──────────────────────────────────────────────────────────
    "Languages": [
        "Python", "Java", "Kotlin", "TypeScript", "JavaScript", "Go", "Rust",
        "C++", "C#", "Swift", "Dart", "R", "Scala", "Bash",
    ],
    "Frameworks": [
        "Spring Boot", "FastAPI", "Django", "Flask", "Node.js", "NestJS",
        "React", "Vue.js", "Angular", "Next.js", "Flutter", "Express.js",
    ],
    "Data & ML": [
        "PyTorch", "TensorFlow", "scikit-learn", "HuggingFace Transformers",
        "Pandas", "NumPy", "FAISS", "LangChain", "Spark", "dbt", "Airflow",
    ],
    "Infrastructure": [
        "Docker", "Kubernetes", "Terraform", "Helm", "AWS", "GCP", "Azure",
        "GitHub Actions", "Jenkins", "ArgoCD", "Ansible",
    ],
    "Data Stores": [
        "PostgreSQL", "MongoDB", "MySQL", "Redis", "Elasticsearch",
        "Cassandra", "Neo4j", "ClickHouse", "DynamoDB", "BigQuery",
    ],
    "Messaging": [
        "Apache Kafka", "RabbitMQ", "NATS", "gRPC", "WebSocket",
    ],
    "Engineering Practices": [
        "Microservices", "Event-Driven Architecture", "CI/CD", "TDD", "DDD",
        "REST API Design", "CQRS", "Agile / Scrum", "Git", "Code Review",
    ],
    # ── Product & Design ─────────────────────────────────────────────────────
    "Product Management": [
        "Product Roadmapping", "User Story Mapping", "OKRs", "A/B Testing",
        "Product Analytics", "Go-to-Market Strategy", "Feature Prioritisation",
        "JIRA", "Confluence", "ProductBoard", "Mixpanel",
    ],
    "UX / Design": [
        "Figma", "Adobe XD", "Sketch", "InVision", "Prototyping",
        "User Research", "Usability Testing", "Wireframing",
        "Design Systems", "Accessibility (WCAG)", "Framer",
    ],
    # ── Business & Analytics ─────────────────────────────────────────────────
    "Analytics Tools": [
        "Tableau", "Power BI", "Looker", "Google Analytics", "Amplitude",
        "Mixpanel", "Excel (Advanced)", "SQL", "Metabase",
    ],
    "Marketing": [
        "SEO / SEM", "Google Ads", "Facebook Ads", "HubSpot", "Salesforce",
        "Email Marketing", "Content Strategy", "Marketing Automation",
        "CRM Management", "TikTok Ads", "Affiliate Marketing",
    ],
    "Finance": [
        "Financial Modelling", "Excel (Advanced)", "Bloomberg Terminal",
        "QuickBooks", "SAP FI", "IFRS", "Budgeting & Forecasting",
        "DCF Valuation", "Power BI", "SQL",
    ],
    "Project Management": [
        "MS Project", "Asana", "Monday.com", "Agile / Scrum", "Kanban",
        "Risk Management", "PMP Methodology", "Resource Planning",
        "JIRA", "Confluence", "Stakeholder Reporting",
    ],
    "Business Analysis": [
        "Requirements Gathering", "Process Mapping", "BPMN",
        "Use Case Analysis", "Gap Analysis", "Stakeholder Workshops",
        "JIRA", "Confluence", "Visio", "SQL",
    ],
    "HR & Talent": [
        "Workday", "SAP SuccessFactors", "LinkedIn Recruiter",
        "Talent Acquisition", "Performance Management", "HRIS",
        "Employee Engagement", "L&D Programme Design", "Compensation & Benefits",
    ],
}

# Preferred skill categories per role (used in generate_cv_data)
ROLE_SKILL_CATS: dict[str, list[str]] = {
    "Software Engineer":          ["Languages", "Frameworks", "Data Stores", "Infrastructure", "Engineering Practices"],
    "Data Engineer":              ["Languages", "Data & ML", "Data Stores", "Infrastructure", "Messaging"],
    "Data Scientist":             ["Languages", "Data & ML", "Analytics Tools", "Data Stores"],
    "Machine Learning Engineer":  ["Languages", "Data & ML", "Infrastructure", "Data Stores"],
    "DevOps / Platform Engineer": ["Infrastructure", "Languages", "Messaging", "Engineering Practices"],
    "Site Reliability Engineer":  ["Infrastructure", "Engineering Practices", "Messaging", "Data Stores"],
    "Cloud Solutions Architect":  ["Infrastructure", "Data Stores", "Languages", "Engineering Practices"],
    "QA / Automation Engineer":   ["Languages", "Frameworks", "Engineering Practices", "Infrastructure"],
    "Security Engineer":          ["Infrastructure", "Languages", "Engineering Practices", "Data Stores"],
    "Mobile Engineer":            ["Languages", "Frameworks", "Engineering Practices"],
    "AI Research Engineer":       ["Languages", "Data & ML", "Infrastructure"],
    "Technical Lead":             ["Languages", "Frameworks", "Engineering Practices", "Infrastructure"],
    "Staff Engineer":             ["Languages", "Infrastructure", "Engineering Practices", "Data & ML"],
    "Product Manager":            ["Product Management", "Analytics Tools", "Project Management"],
    "UX / UI Designer":           ["UX / Design", "Product Management", "Analytics Tools"],
    "Business Analyst":           ["Business Analysis", "Analytics Tools", "Project Management"],
    "Project Manager":            ["Project Management", "Business Analysis", "Engineering Practices"],
    "Data Analyst":               ["Analytics Tools", "Languages", "Data Stores", "Business Analysis"],
    "Digital Marketing Manager":  ["Marketing", "Analytics Tools", "Project Management"],
    "HR Manager":                 ["HR & Talent", "Project Management", "Analytics Tools"],
    "Finance Analyst":            ["Finance", "Analytics Tools", "Business Analysis"],
    "Solutions Consultant":       ["Business Analysis", "Project Management", "Languages"],
    "Operations Manager":         ["Project Management", "Business Analysis", "Analytics Tools"],
    "Customer Success Manager":   ["Product Management", "Analytics Tools", "Project Management"],
}

# Roles that typically don't have a GitHub profile
_NO_GITHUB_ROLES = {
    "Product Manager", "UX / UI Designer", "Business Analyst", "Project Manager",
    "Digital Marketing Manager", "HR Manager", "Finance Analyst",
    "Operations Manager", "Customer Success Manager",
}

SOFT_SKILLS = [
    "Leadership & Mentoring", "Cross-functional Collaboration",
    "Technical Communication", "Problem Decomposition",
    "Project Planning", "Conflict Resolution",
    "Stakeholder Management", "Analytical Thinking",
    "Ownership & Accountability", "Continuous Learning",
    "Negotiation", "Presentation Skills", "Data-Driven Decision Making",
    "Empathy & Active Listening", "Time Management",
]

JOB_ROLES = [
    # Engineering
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
    # Product & Design
    ("Product Manager", ""),
    ("Product Manager", "Growth"),
    ("UX / UI Designer", ""),
    # Business
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

COMPANIES = [
    # Vietnamese tech
    "Grab Vietnam", "Shopee Vietnam", "VNG Corporation", "FPT Software",
    "Tiki Corporation", "MoMo (M-Service)", "Zalo Group", "VinAI Research",
    "VNPT Technology", "CMC Global", "Techcombank", "MB Bank Tech",
    "KMS Technology", "Rikkeisoft", "NashTech Vietnam", "Base.vn", "Sky Mavis",
    "Gameloft Vietnam", "Siemens Technology Vietnam", "Oracle Vietnam",
    "Viettel High-Tech", "LogiGear Corporation", "ThoughtWorks Vietnam",
    # Global tech
    "Google LLC", "Meta Platforms", "Atlassian",
    "Bosch Vietnam", "Samsung R&D Institute Vietnam",
    "Microsoft Vietnam", "Amazon Web Services Vietnam",
    # Banking & Finance
    "ACB Bank", "Sacombank", "VPBank", "HDBank", "Viet Capital Bank",
    "Manulife Vietnam", "AIA Vietnam", "Dragon Capital",
    # Consulting
    "Deloitte Vietnam", "KPMG Vietnam", "PwC Vietnam",
    "McKinsey & Company Vietnam", "BCG Vietnam",
    # Consumer & Retail
    "Vingroup", "Masan Group", "AEON Vietnam", "Lotte Vietnam",
    "Unilever Vietnam", "Nestlé Vietnam", "Abbott Vietnam",
    # Startups & Digital
    "Haravan", "TopCV Vietnam", "Sendo.vn", "Medici Health",
    "GHN Express", "GHTK", "Be Group",
]

UNIVERSITIES = [
    # Tech / Engineering
    ("Ho Chi Minh City University of Technology and Education", "HCMUTE"),
    ("Ho Chi Minh City University of Technology", "HCMUT – Bach Khoa"),
    ("University of Information Technology – VNU HCM", "UIT"),
    ("Ho Chi Minh City University of Science – VNU HCM", "HCMUS"),
    ("FPT University Ho Chi Minh City", "FPT University"),
    ("Hanoi University of Science and Technology", "HUST"),
    ("Vietnam National University – Hanoi", "VNU-HN"),
    ("Can Tho University", "CTU"),
    ("Da Nang University of Science and Technology", "UT DaNang"),
    ("RMIT University Vietnam", "RMIT Vietnam"),
    # Business / Economics
    ("Ho Chi Minh City University of Economics", "UEH"),
    ("Foreign Trade University – HCMC Campus", "FTU HCMC"),
    ("Banking University of Ho Chi Minh City", "BUH"),
    ("University of Economics and Law – VNU HCM", "UEL"),
    ("National Economics University", "NEU – Hanoi"),
    ("Ho Chi Minh City Open University", "OU"),
    ("Ho Chi Minh City University of Law", "ULaw"),
]

DEGREE_MAP = {
    # Engineering
    "Software Engineer":              "Bachelor of Engineering in Software Engineering",
    "Data Engineer":                  "Bachelor of Science in Computer Science",
    "Data Scientist":                 "Bachelor of Science in Data Science",
    "Machine Learning Engineer":      "Bachelor of Science in Artificial Intelligence",
    "DevOps / Platform Engineer":     "Bachelor of Engineering in Computer Engineering",
    "Site Reliability Engineer":      "Bachelor of Engineering in Computer Engineering",
    "Cloud Solutions Architect":      "Bachelor of Science in Information Technology",
    "QA / Automation Engineer":       "Bachelor of Engineering in Software Engineering",
    "Security Engineer":              "Bachelor of Science in Cybersecurity",
    "Mobile Engineer":                "Bachelor of Engineering in Software Engineering",
    "AI Research Engineer":           "Master of Science in Computer Science",
    "Technical Lead":                 "Master of Engineering in Software Engineering",
    "Staff Engineer":                 "Master of Science in Computer Science",
    # Product & Design
    "Product Manager":                "Bachelor of Business Administration",
    "UX / UI Designer":               "Bachelor of Arts in Design / Human-Computer Interaction",
    # Business
    "Business Analyst":               "Bachelor of Business Administration",
    "Project Manager":                "Bachelor of Business Administration",
    "Data Analyst":                   "Bachelor of Science in Statistics & Data Analytics",
    "Digital Marketing Manager":      "Bachelor of Arts in Marketing & Communications",
    "HR Manager":                     "Bachelor of Business Administration in Human Resources",
    "Finance Analyst":                "Bachelor of Finance & Accounting",
    "Solutions Consultant":           "Bachelor of Science in Information Systems",
    "Operations Manager":             "Bachelor of Business Administration in Operations",
    "Customer Success Manager":       "Bachelor of Business Administration",
}

CERTS = [
    # Engineering
    "AWS Certified Solutions Architect - Professional",
    "Google Cloud Professional Data Engineer",
    "CKA: Certified Kubernetes Administrator",
    "Oracle Certified Professional: Java SE 17",
    "Microsoft Azure AI Engineer Associate (AI-102)",
    "TensorFlow Developer Certificate",
    "HashiCorp Certified: Terraform Associate",
    "CKAD: Certified Kubernetes Application Developer",
    "AWS Certified Machine Learning - Specialty",
    "CompTIA Security+",
    # PM / Agile
    "Certified Scrum Master (CSM) - Scrum Alliance",
    "PMI Agile Certified Practitioner (PMI-ACP)",
    "PMP - Project Management Professional",
    "PRINCE2 Foundation",
    # Business / Marketing
    "Google Analytics Individual Qualification (GAIQ)",
    "HubSpot Inbound Marketing Certification",
    "Meta Blueprint: Digital Marketing Associate",
    "Google UX Design Professional Certificate",
    "Certified Business Analysis Professional (CBAP)",
    "Six Sigma Green Belt (CSSGB)",
    # Finance
    "CFA Level I – CFA Institute",
    "ACCA (Association of Chartered Certified Accountants) – Part Qualified",
    "Certified Management Accountant (CMA)",
]

LANGS = [
    ("English", "Native / Bilingual Proficiency"),
    ("English", "Full Professional Proficiency (IELTS 7.5)"),
    ("English", "Professional Working Proficiency (TOEIC 900)"),
    ("English", "Professional Working Proficiency (TOEIC 720)"),
    ("Japanese", "Business Level (JLPT N2)"),
    ("Japanese", "Elementary (JLPT N4)"),
    ("Korean", "Elementary (TOPIK Level 2)"),
    ("Chinese (Mandarin)", "Elementary (HSK 2)"),
    ("French", "Intermediate (DELF B1)"),
]

ACTIVITIES = [
    # Tech
    "Google Developer Student Club (GDSC) - Technical Lead, organized 4 workshops per semester.",
    "Open Source Contributor - maintained a FastAPI authentication library with 800+ GitHub stars.",
    "HackIT National Hackathon 2024 - Runner-up; built a real-time traffic prediction system.",
    "ACM ICPC Regionals 2023 - represented HCMUTE, reached regional finals.",
    "Technical Mentor at CodePath Vietnam - guided 20+ students through data structures curriculum.",
    "Viet Youth Tech Conference 2025 - speaker on Microservices best practices.",
    "Google Hash Code 2024 - Top 15% globally, team of 4.",
    "FPT Edu AI Challenge 2024 - 1st place; deployed on-device speech-to-text model.",
    # Product / Business
    "Young Business Leaders Program (YBL) 2024 - finalist; pitched a D2C startup concept to VCs.",
    "TEDx HCMUT - Speaker on 'Human-Centred Design in Emerging Markets'.",
    "Product Hunt Community Vietnam - co-organiser of monthly product teardown sessions.",
    "McKinsey Case Competition 2024 - team led by candidate won 1st place (national round).",
    "Startup Weekend Saigon 2025 - built and pitched a PropTech MVP in 54 hours, won 2nd place.",
    "Marketing Association HCMUTE - VP of External Relations, grew membership by 40% in one year.",
    "CFA Institute Research Challenge 2024 - team represented NEU, reached national finals.",
    "Ho Chi Minh City Marathon 2024 - volunteer logistics coordinator for 3,000+ runners.",
]

ACHIEVEMENT_TEMPLATES = [
    # Engineering
    "Reduced end-to-end API latency by {pct}% by introducing {tech} caching and query optimisation.",
    "Led migration of {n} monolithic services to microservices, cutting deployment time by {pct}%.",
    "Designed a {pattern} pipeline that scaled to {rps}k requests/minute with 99.95% uptime.",
    "Automated {process} workflows, saving the team approximately {hrs} engineering-hours per sprint.",
    "Mentored {n} junior engineers through weekly 1-on-1s, code reviews, and design sessions.",
    "Built a real-time analytics dashboard used by {users}k monthly active users.",
    "Delivered {feature} feature {weeks} weeks ahead of schedule, contributing to a {pct}% revenue uplift.",
    "Integrated {tech} observability stack; MTTR dropped from {hrs}h to under {min}min.",
    "Spearheaded adoption of TDD practices; unit-test coverage increased from {lo}% to {hi}%.",
    "Authored internal RFC for {pattern} architecture, adopted across {n} product squads.",
    "Reduced Docker image sizes by {pct}% via multi-stage builds, halving cold-start times in k8s.",
    "Implemented RBAC + {tech} authentication, achieving SOC 2 Type II compliance requirements.",
    "Consolidated {n} redundant microservices, reducing infra cost by ${cost}k/yr.",
    "Set up {tech} CI/CD pipeline with zero-downtime blue-green deployments across {n} environments.",
    "Collaborated with ML team to serve a model at <{lat}ms p99 latency using ONNX Runtime.",
    # Product & Design
    "Defined and shipped {n} major product features that increased DAU by {pct}%.",
    "Redesigned {feature} onboarding flow; user activation rate improved by {pct}% in {weeks} weeks.",
    "Drove {pct}% improvement in {metric} through {n} A/B experiments across the {segment} segment.",
    "Conducted {n} rounds of user research, surfacing {n} critical usability issues before launch.",
    "Led {n}-person cross-functional squad through a full product cycle from discovery to GA.",
    "Reduced design-to-dev handoff cycle time by {pct}% by introducing a shared Figma design system.",
    # Business & Analytics
    "Grew {segment} segment revenue by {pct}% YoY through {n} data-driven go-to-market initiatives.",
    "Reduced customer acquisition cost by {pct}% by optimising {channel} spend allocation.",
    "Led cross-functional team of {n} to deliver {feature} launch across {country} in {weeks} weeks.",
    "Built financial model for ${revenue_m}M capex project; approved by CFO within {weeks} weeks.",
    "Launched {channel} campaign reaching {users}k impressions with {pct}% above-benchmark CTR.",
    "Conducted {n} stakeholder workshops; translated findings into {n} actionable OKRs.",
    "Managed portfolio of {n} concurrent projects totalling ${revenue_m}M in combined budget.",
    "Negotiated ${revenue_m}M vendor contract, achieving {pct}% cost savings vs. prior agreement.",
    "Streamlined {dept} operations with process automation, reducing manual effort by {pct}%.",
    "Recruited and onboarded {n} specialist hires for the {dept} function in under {weeks} weeks.",
    "Automated {n} manual reporting workflows, reclaiming {hrs}h/week across the analytics team.",
]


def _bullet(tmpl: str) -> str:
    return tmpl.format(
        pct=random.randint(15, 65),
        n=random.randint(2, 12),
        tech=random.choice(["Redis", "Kafka", "Prometheus", "OpenTelemetry", "ONNX", "JWT", "OAuth2"]),
        pattern=random.choice(["CQRS", "event-sourcing", "saga", "hexagonal", "strangler-fig"]),
        rps=random.choice([10, 50, 100, 250, 500]),
        process=random.choice(["CI/CD", "data ingestion", "reporting", "infra provisioning"]),
        hrs=random.randint(4, 24),
        users=random.randint(10, 500),
        feature=random.choice(["payment", "search", "notification", "analytics", "auth", "checkout",
                                "onboarding", "dashboard", "reporting", "referral"]),
        weeks=random.randint(1, 6),
        min=random.randint(2, 15),
        lo=random.randint(20, 50),
        hi=random.randint(75, 98),
        cost=random.randint(5, 80),
        lat=random.choice([20, 50, 80, 120]),
        metric=random.choice(["NPS", "DAU", "MAU", "conversion rate", "retention rate", "LTV", "CAC"]),
        segment=random.choice(["SME", "enterprise", "B2C", "B2B", "e-commerce", "mid-market"]),
        channel=random.choice(["organic search", "paid social", "email", "content marketing",
                                "in-app", "influencer"]),
        revenue_m=random.randint(1, 50),
        country=random.choice(["Vietnam", "SEA markets", "APAC", "3 key markets"]),
        dept=random.choice(["engineering", "product", "marketing", "sales", "operations", "HR"]),
    )


# ─────────────────────────────────────────────────────────────────────────────
# CONTENT HELPERS — career arc, rich experience strings, diverse summaries
# ─────────────────────────────────────────────────────────────────────────────

_CAREER_ARC: dict[str, list[str]] = {
    "Software Engineer":          ["Junior Software Engineer", "Software Engineer", "Senior Software Engineer", "Lead Software Engineer", "Principal Engineer"],
    "Data Engineer":              ["Data Engineer I", "Data Engineer", "Senior Data Engineer", "Lead Data Engineer", "Principal Data Engineer"],
    "Data Scientist":             ["Data Analyst", "Data Scientist", "Senior Data Scientist", "Lead Data Scientist"],
    "Machine Learning Engineer":  ["ML Engineer", "Senior ML Engineer", "Lead ML Engineer", "Principal ML Engineer"],
    "DevOps / Platform Engineer": ["DevOps Engineer", "Senior DevOps Engineer", "Platform Engineer", "Lead Platform Engineer"],
    "Site Reliability Engineer":  ["SRE", "Senior SRE", "Lead SRE", "Principal SRE"],
    "Cloud Solutions Architect":  ["Cloud Engineer", "Solutions Architect", "Senior Solutions Architect", "Principal Architect"],
    "QA / Automation Engineer":   ["QA Engineer", "SDET", "Senior QA Engineer", "QA Lead"],
    "Security Engineer":          ["Security Analyst", "Security Engineer", "Senior Security Engineer", "Lead Security Engineer"],
    "Mobile Engineer":            ["Mobile Developer", "iOS/Android Engineer", "Senior Mobile Engineer", "Lead Mobile Engineer"],
    "AI Research Engineer":       ["Research Engineer", "ML Research Engineer", "Senior Research Engineer", "Principal Research Engineer"],
    "Technical Lead":             ["Software Engineer", "Senior Software Engineer", "Tech Lead", "Engineering Manager"],
    "Staff Engineer":             ["Senior Software Engineer", "Staff Engineer", "Senior Staff Engineer", "Distinguished Engineer"],
    "Product Manager":            ["Associate Product Manager", "Product Manager", "Senior PM", "Group PM"],
    "UX / UI Designer":           ["UI Designer", "UX Designer", "Senior UX Designer", "Lead UX Designer"],
    "Business Analyst":           ["Junior Business Analyst", "Business Analyst", "Senior BA", "Lead BA"],
    "Project Manager":            ["Project Coordinator", "Project Manager", "Senior PM", "Programme Manager"],
    "Data Analyst":               ["Junior Data Analyst", "Data Analyst", "Senior Data Analyst", "Lead Data Analyst"],
    "Digital Marketing Manager":  ["Marketing Coordinator", "Digital Marketing Specialist", "Digital Marketing Manager", "Head of Digital Marketing"],
    "HR Manager":                 ["HR Coordinator", "HR Specialist", "HR Manager", "Senior HR Manager"],
    "Finance Analyst":            ["Financial Analyst I", "Financial Analyst", "Senior Financial Analyst", "Finance Manager"],
    "Solutions Consultant":       ["Junior Consultant", "Solutions Consultant", "Senior Solutions Consultant", "Principal Consultant"],
    "Operations Manager":         ["Operations Analyst", "Operations Specialist", "Operations Manager", "Senior Operations Manager"],
    "Customer Success Manager":   ["Customer Success Associate", "CSM", "Senior CSM", "Head of Customer Success"],
}


def _career_title(role_base: str, company_idx: int, n_companies: int) -> str:
    """Map position in career history to a realistic job title. idx=0 oldest → most junior."""
    arc = _CAREER_ARC.get(role_base, [f"Junior {role_base}", role_base, f"Senior {role_base}", f"Lead {role_base}"])
    arc_pos = max(0, len(arc) - n_companies + company_idx)
    return arc[min(arc_pos, len(arc) - 1)]


def _build_experience_string(companies: list[str], role_base: str, skill_items: list[str]) -> str:
    """Career-arc titles + achievement snippets for top-2 most-recent roles. ≤512 chars."""
    n = len(companies)
    entries: list[tuple[str, str, str]] = []  # (title, company, date_str)
    offset = 0
    for i, company in enumerate(companies):
        title = _career_title(role_base, i, n)
        s, e = _date_range(min_months=8, max_months=36, end_offset_days=offset)
        offset += random.randint(90, 400)
        entries.append((title, company, f"{s}–{e}"))
    entries.reverse()  # most recent first

    parts = []
    for j, (title, company, dates) in enumerate(entries):
        if j < 2:
            ach = _bullet(random.choice(ACHIEVEMENT_TEMPLATES))[:100]
            parts.append(f"{title} @ {company} ({dates}) · {ach}")
        else:
            parts.append(f"{title} @ {company} ({dates})")
    return " | ".join(parts)[:512]


_DOMAINS = [
    "fintech", "e-commerce", "SaaS", "healthtech", "edtech",
    "logistics", "media & entertainment", "B2B software", "proptech", "insurtech",
]


def _build_summary(role_base: str, full_role: str, years_exp: int, skill_items: list[str]) -> str:
    """Pick from 10+ diverse realistic summary templates. ≤400 chars."""
    s1 = random.choice(skill_items) if skill_items else role_base
    s2 = random.choice(skill_items) if len(skill_items) > 1 else s1
    s3 = random.choice(skill_items) if len(skill_items) > 2 else s2

    sw  = ("junior" if years_exp <= 2 else "mid-level" if years_exp <= 5
           else "senior" if years_exp <= 10 else "principal-level")
    dom = random.choice(_DOMAINS)
    pct = random.randint(20, 55)
    n_t = random.randint(4, 14)
    n_s = random.randint(3, 10)

    _is_tech = role_base in {
        "Software Engineer", "Data Engineer", "Data Scientist", "Machine Learning Engineer",
        "DevOps / Platform Engineer", "Site Reliability Engineer", "Cloud Solutions Architect",
        "QA / Automation Engineer", "Security Engineer", "Mobile Engineer",
        "AI Research Engineer", "Technical Lead", "Staff Engineer",
    }
    _is_product = role_base in {"Product Manager", "UX / UI Designer"}

    if _is_tech:
        tpls = [
            f"{years_exp}+ years as a {full_role} specialising in {s1} and {s2}. Designed and maintained production systems serving millions of users. Strong advocate of clean architecture, TDD, and continuous delivery.",
            f"Results-driven {sw} {role_base} with {years_exp} years building high-throughput {dom} platforms. Deep expertise in {s1}, {s2}, and {s3}. Comfortable owning systems end-to-end from architecture through on-call.",
            f"Versatile {full_role} ({years_exp} yrs) with a track record of delivering complex distributed systems on time. Proficient in {s1} and {s2}. Mentored {n_t} engineers and led cross-functional squads in fast-moving Agile environments.",
            f"Engineering-focused problem solver with {years_exp} years in {dom} startups and scale-ups. Hands-on with {s1}, {s2}, and {s3}. Passionate about observability, platform reliability, and engineering culture.",
            f"{sw.capitalize()} {role_base} who thrives at the intersection of technical depth and delivery speed. {years_exp} years shipping production code in {s1} and {s2}. Cut release cycle by {pct}% through CI/CD automation at last role.",
            f"Pragmatic {full_role} with {years_exp} years across {dom} and enterprise software. Skilled in {s1}, {s2}, and {s3}. Known for writing clean, well-tested code and systematically raising engineering standards across the team.",
            f"Open-source contributor and {sw} {role_base} with {years_exp} years of hands-on experience. Strong foundation in {s1} and {s2}. Led refactoring of {n_s} legacy services, reducing infrastructure cost by {pct}%.",
            f"Systems thinker with {years_exp} years in {role_base} roles at high-growth companies. Deep expertise in {s1}; solid grounding in {s2} and {s3}. Recognised for bridging engineering velocity with product quality.",
            f"Detail-oriented {sw} {full_role} ({years_exp} yrs) with end-to-end SDLC ownership. Proficient in {s1} and {s2}. Active technical mentor — helped grow the team from {n_t} to {n_t + 6} engineers.",
            f"Hands-on {role_base} with {years_exp} years building resilient, observable microservices in {dom}. Expertise in {s1} and {s2}. Drove adoption of {s3}-based tooling, improving MTTR by {pct}%.",
        ]
    elif _is_product:
        tpls = [
            f"{sw.capitalize()} {full_role} with {years_exp}+ years leading product discovery, roadmapping, and go-to-market in {dom}. Proficient in {s1} and {s2}. Passionate about shipping user-centred products that move business KPIs.",
            f"Customer-obsessed {role_base} ({years_exp} yrs) bridging user needs and engineering constraints. Strong command of {s1}, {s2}, and {s3}. Shipped {random.randint(5, 20)} major features across consumer and B2B products.",
            f"Data-driven {full_role} with {years_exp} years in {dom}. Uses {s1} and {s2} to run experiments, set OKRs, and prioritise ruthlessly. Track record of {pct}% uplift in activation within {random.randint(4, 12)} weeks of launch.",
            f"Strategic {role_base} who turns ambiguous opportunities into clear product specs. {years_exp} years of experience with {s1} and {s2}. Led {n_t}-person cross-functional squads through 0-to-1 product launches in {dom}.",
            f"Growth-minded {sw} {full_role} ({years_exp} yrs). Deep knowledge of {s1} and {s2}; comfortable in both qualitative research and quantitative A/B testing. Consistently delivers products users love.",
            f"T-shaped {role_base} with {years_exp} years spanning mobile, platform, and growth products. Leverages {s1} and {s2} to make fast, informed prioritisation decisions; drives alignment across engineering, design, and business.",
        ]
    else:
        tpls = [
            f"{sw.capitalize()} {full_role} with {years_exp}+ years delivering data-driven insights in {dom}. Proficient in {s1} and {s2}. Trusted by stakeholders to translate complex data into clear, actionable strategies.",
            f"Commercially minded {role_base} ({years_exp} yrs) with a track record of driving {pct}% efficiency gains. Expertise in {s1} and {s2}. Consistently bridges analytical rigour with practical business judgment.",
            f"Collaborative {full_role} with {years_exp} years of cross-functional experience in {dom}. Skilled in {s1}, {s2}, and {s3}. Brings a structured, data-informed approach to stakeholder alignment and problem-solving.",
            f"Detail-oriented {sw} {role_base} with {years_exp} years optimising operations and delivering insights. Strong command of {s1} and {s2}. Managed portfolios of up to ${random.randint(5, 50)}M across {n_s} concurrent projects.",
            f"Results-oriented {full_role} ({years_exp} yrs) excelling at requirements gathering, root-cause analysis, and change management. Proficient in {s1} and {s2}; comfortable presenting findings directly to C-suite stakeholders.",
            f"Analytical {role_base} with {years_exp} years in {dom} and financial services. Deep expertise in {s1}; working knowledge of {s2} and {s3}. Known for building robust models that withstand rigorous executive scrutiny.",
            f"Strategic {sw} {full_role} ({years_exp} yrs) combining domain expertise with {s1} and {s2} proficiency. Led {n_t}-person teams and delivered {n_s} cross-functional initiatives on time and within budget.",
        ]

    return random.choice(tpls)[:400]


# ─────────────────────────────────────────────────────────────────────────────
# DATA GENERATION
# ─────────────────────────────────────────────────────────────────────────────
def _date_range(min_months=6, max_months=48, end_offset_days=0) -> tuple[str, str]:
    end = date.today() - timedelta(days=end_offset_days)
    duration = timedelta(days=random.randint(min_months * 30, max_months * 30))
    start = end - duration
    end_s = "Present" if end_offset_days < 90 else end.strftime("%b %Y")
    return start.strftime("%b %Y"), end_s


def generate_cv_data(index: int) -> dict:
    Faker.seed(index * 37 + 11)
    random.seed(index * 19 + 5)
    fake = Faker("en_US")

    role_base, speciality = random.choice(JOB_ROLES)
    full_role = f"{role_base} ({speciality})" if speciality else role_base
    years_exp = random.randint(1, 16)

    name     = fake.name()
    email    = fake.email()
    phone    = fake.phone_number()[:16]
    city     = random.choice(["Ho Chi Minh City", "Hanoi", "Da Nang", "Bien Hoa", "Can Tho"])
    linkedin = f"linkedin.com/in/{fake.user_name()}"
    github   = f"github.com/{fake.user_name()}" if role_base not in _NO_GITHUB_ROLES else ""

    # Companies
    num_jobs = random.randint(2, 5)
    companies = random.sample(COMPANIES, num_jobs)

    # University
    uni_full, uni_short = random.choice(UNIVERSITIES)
    degree = DEGREE_MAP.get(role_base, "Bachelor of Science in Computer Science")
    grad_year = date.today().year - years_exp - random.randint(0, 3)
    gpa_raw = round(random.uniform(6.5, 9.9), 2)
    gpa_str = f"GPA: {gpa_raw}/10" if random.random() > 0.3 else ""
    toeic = random.choice([500, 600, 650, 700, 720, 750, 800, 860, 900])

    # Skills — pick categories relevant to this role
    cat_pool = ROLE_SKILL_CATS.get(role_base, list(SKILLS_BY_CAT.keys()))
    chosen_cats = random.sample(cat_pool, k=min(random.randint(3, 5), len(cat_pool)))
    skill_items: list[str] = []
    for cat in chosen_cats:
        pool = SKILLS_BY_CAT.get(cat, [])
        skill_items += random.sample(pool, k=min(random.randint(2, 4), len(pool)))
    skill_items = list(dict.fromkeys(skill_items))  # deduplicate, preserve order
    soft = random.sample(SOFT_SKILLS, k=random.randint(3, 5))

    # ── build 4 model fields ──────────────────────────────────────────────────
    resume_summary    = _build_summary(role_base, full_role, years_exp, skill_items)
    resume_experience = _build_experience_string(companies, role_base, skill_items)

    # resume_skills (≤ 300)
    all_skills = skill_items + soft
    resume_skills = ", ".join(all_skills)[:300]

    # resume_education (≤ 256)
    cert = random.choice(CERTS) if random.random() > 0.4 else ""
    edu_parts = [f"{degree} – {uni_short}, {grad_year}"]
    if gpa_str:
        edu_parts.append(gpa_str)
    edu_parts.append(f"TOEIC {toeic}")
    if cert:
        edu_parts.append(f"Cert: {cert}")
    resume_education = " | ".join(edu_parts)[:256]

    # Full raw CV text (display only)
    resume_text = (
        f"Name: {name}\nEmail: {email} | Phone: {phone}\n"
        f"Location: {city}, Vietnam | LinkedIn: {linkedin} | GitHub: {github}\n\n"
        f"OBJECTIVE\n{resume_summary}\n\n"
        f"EXPERIENCE\n{resume_experience}\n\n"
        f"SKILLS\n{resume_skills}\n\n"
        f"EDUCATION\n{resume_education}"
    )

    return {
        "resume_index": index,
        # 4 model fields
        "resume_summary":    resume_summary,
        "resume_experience": resume_experience,
        "resume_skills":     resume_skills,
        "resume_education":  resume_education,
        "resume_text":       resume_text,
        # PDF rendering metadata
        "_meta": {
            "name": name, "email": email, "phone": phone,
            "city": city, "linkedin": linkedin, "github": github,
            "role": full_role, "role_base": role_base,
            "years_exp": years_exp,
            "companies": companies,
            "uni_full": uni_full, "uni_short": uni_short,
            "degree": degree, "grad_year": grad_year,
            "gpa_str": gpa_str, "toeic": toeic,
            "skill_items": skill_items,
            "soft_skills": soft,
            "chosen_cats": chosen_cats,
            "cert": cert,
        },
    }


# ─────────────────────────────────────────────────────────────────────────────
# JOB-AWARE CV GENERATION
# ─────────────────────────────────────────────────────────────────────────────

_ROLE_KEYWORDS: dict[str, list[str]] = {
    "Software Engineer":          ["software engineer", "backend", "back-end", "back end", "fullstack", "full-stack", "full stack", "web engineer", "application developer"],
    "Data Engineer":              ["data engineer", "etl", "data pipeline", "data platform", "data infrastructure"],
    "Data Scientist":             ["data scientist", "data science"],
    "Machine Learning Engineer":  ["machine learning", "ml engineer", "ai engineer", "deep learning", "llm engineer"],
    "DevOps / Platform Engineer": ["devops", "platform engineer", "infrastructure engineer", "cloud engineer", "infra engineer"],
    "Site Reliability Engineer":  ["sre", "site reliability", "reliability engineer"],
    "Cloud Solutions Architect":  ["solutions architect", "cloud architect", "cloud solutions", "enterprise architect"],
    "QA / Automation Engineer":   ["qa engineer", "quality assurance", "automation engineer", "sdet", "test engineer", "software tester"],
    "Security Engineer":          ["security engineer", "infosec", "cybersecurity", "appsec", "security analyst"],
    "Mobile Engineer":            ["mobile engineer", "android", "ios", "flutter developer", "react native", "mobile developer"],
    "AI Research Engineer":       ["ai research", "research engineer", "nlp engineer", "computer vision", "research scientist"],
    "Technical Lead":             ["tech lead", "technical lead", "engineering lead", "lead engineer", "lead developer"],
    "Staff Engineer":             ["staff engineer", "principal engineer", "distinguished engineer"],
    "Product Manager":            ["product manager", "product owner", "head of product", "vp of product"],
    "UX / UI Designer":           ["ux designer", "ui designer", "product designer", "user experience", "ux/ui", "ui/ux", "interaction designer"],
    "Business Analyst":           ["business analyst", "business analysis", "systems analyst", "functional analyst"],
    "Project Manager":            ["project manager", "programme manager", "scrum master", "pmo", "delivery manager"],
    "Data Analyst":               ["data analyst", "analytics engineer", "business intelligence", "bi analyst", "reporting analyst"],
    "Digital Marketing Manager":  ["marketing manager", "digital marketing", "growth manager", "seo specialist", "performance marketing"],
    "HR Manager":                 ["hr manager", "human resources", "talent acquisition", "people manager", "recruiter", "people & culture"],
    "Finance Analyst":            ["finance analyst", "financial analyst", "fp&a", "accounting", "treasury analyst"],
    "Solutions Consultant":       ["solutions consultant", "pre-sales", "solutions engineer", "technical consultant", "implementation consultant"],
    "Operations Manager":         ["operations manager", "ops manager", "head of operations", "supply chain manager", "operations lead"],
    "Customer Success Manager":   ["customer success", "csm", "account manager", "client success", "customer experience"],
}


def _find_matching_roles(job_title: str) -> list[tuple[str, str]]:
    """Return JOB_ROLES entries matching job title via keyword table, with word-overlap fallback."""
    t = job_title.lower()
    matched = [
        (rb, sp) for rb, sp in JOB_ROLES
        if any(kw in t for kw in _ROLE_KEYWORDS.get(rb, [rb.lower()]))
    ]
    if not matched:
        t_words = set(t.split())
        matched = [
            (rb, sp) for rb, sp in JOB_ROLES
            if set(rb.lower().split()) & t_words
        ]
    return matched or list(JOB_ROLES)


def _parse_job_spec(source) -> dict:
    """
    Normalise a job spec from:
      - dict / str path to JSON  — supports both script format AND job-service API response format
      - argparse Namespace with job_title / job_skills / job_level / job_mix_rate

    Handles job-service field aliases:
      postId        → job_id
      skillNames    → skills
      experienceLevel (ENTRY/INTERMEDIATE/SENIOR/LEAD) → level

    Returns: { title, skills (list[str]), level, mix_rate, job_id }
    """
    if isinstance(source, dict):
        raw = source
    elif isinstance(source, str):
        with open(source, encoding="utf-8") as f:
            raw = json.load(f)
    else:
        # argparse Namespace
        raw = {
            "title":    getattr(source, "job_title",    ""),
            "skills":   getattr(source, "job_skills",   ""),
            "level":    getattr(source, "job_level",    ""),
            "mix_rate": getattr(source, "job_mix_rate", 0.7),
        }

    # job_id — job-service uses "postId"
    job_id = str(
        raw.get("job_id") or raw.get("postId") or raw.get("id") or ""
    )

    # title — direct or job-service "title"
    title = raw.get("title") or raw.get("job_title") or raw.get("name") or ""

    # skills — script uses "skills", job-service returns "skillNames"
    skills_raw = raw.get("skills") or raw.get("skillNames") or raw.get("required_skills") or []
    if isinstance(skills_raw, str):
        skills = [s.strip() for s in skills_raw.replace(";", ",").split(",") if s.strip()]
    else:
        skills = list(skills_raw)

    # level — script uses "level", job-service uses "experienceLevel" enum
    _exp_level_map = {
        "ENTRY":        "junior",
        "INTERMEDIATE": "mid",
        "SENIOR":       "senior",
        "LEAD":         "senior",
    }
    level_raw = raw.get("level") or raw.get("job_level") or raw.get("experienceLevel") or ""
    level = _exp_level_map.get(level_raw.upper(), level_raw.lower())

    return {
        "title":    title,
        "skills":   skills,
        "level":    level,
        "mix_rate": float(raw.get("mix_rate", 0.7)),
        "job_id":   job_id,
    }


def generate_cv_data_for_job(index: int, job_spec: dict) -> dict:
    """
    Generate CV data biased toward a specific job spec.
    job_spec (from _parse_job_spec): title, skills, level, mix_rate, job_id.
    """
    Faker.seed(index * 37 + 11)
    random.seed(index * 19 + 5)
    fake = Faker("en_US")

    job_skills: list[str] = job_spec.get("skills", [])
    mix_rate: float        = job_spec.get("mix_rate", 0.7)
    job_title: str         = job_spec.get("title", "")

    # ── Role selection ────────────────────────────────────────────────────────
    matched_roles = _find_matching_roles(job_title)
    if random.random() < mix_rate:
        role_base, speciality = random.choice(matched_roles)
    else:
        role_base, speciality = random.choice(JOB_ROLES)

    full_role  = f"{role_base} ({speciality})" if speciality else role_base
    years_exp  = random.randint(1, 16)

    # ── Contact info ──────────────────────────────────────────────────────────
    name     = fake.name()
    email    = fake.email()
    phone    = fake.phone_number()[:16]
    city     = random.choice(["Ho Chi Minh City", "Hanoi", "Da Nang", "Bien Hoa", "Can Tho"])
    linkedin = f"linkedin.com/in/{fake.user_name()}"
    github   = f"github.com/{fake.user_name()}" if role_base not in _NO_GITHUB_ROLES else ""

    # ── Companies ─────────────────────────────────────────────────────────────
    num_jobs  = random.randint(2, 5)
    companies = random.sample(COMPANIES, num_jobs)

    # ── Education ─────────────────────────────────────────────────────────────
    uni_full, uni_short = random.choice(UNIVERSITIES)
    degree    = DEGREE_MAP.get(role_base, "Bachelor of Science in Computer Science")
    grad_year = date.today().year - years_exp - random.randint(0, 3)
    gpa_raw   = round(random.uniform(6.5, 9.9), 2)
    gpa_str   = f"GPA: {gpa_raw}/10" if random.random() > 0.3 else ""
    toeic     = random.choice([500, 600, 650, 700, 720, 750, 800, 860, 900])

    # ── Skills — 3 tiers to create realistic ranking distribution ────────────
    # tier 0 (40%): strong match — inject almost all job skills
    # tier 1 (35%): partial match — inject ~50% job skills
    # tier 2 (25%): weak match — inject ~20% job skills + possibly different role
    _tier = 0 if index % 10 < 4 else (1 if index % 10 < 8 else 2)

    cat_pool    = ROLE_SKILL_CATS.get(role_base, list(SKILLS_BY_CAT.keys()))
    chosen_cats = random.sample(cat_pool, k=min(random.randint(3, 5), len(cat_pool)))
    bg_skills: list[str] = []
    for cat in chosen_cats:
        pool = SKILLS_BY_CAT.get(cat, [])
        bg_skills += random.sample(pool, k=min(random.randint(2, 4), len(pool)))

    if job_skills:
        inject_rate = {0: 0.9, 1: 0.5, 2: 0.2}[_tier]
        n_inject = max(1, round(len(job_skills) * inject_rate))
        injected = random.sample(job_skills, k=min(n_inject, len(job_skills)))
    else:
        injected = []
    skill_items = list(dict.fromkeys(injected + bg_skills))[:15]

    soft = random.sample(SOFT_SKILLS, k=random.randint(3, 5))

    # ── Summary & Experience via shared helpers ───────────────────────────────
    resume_summary    = _build_summary(role_base, full_role, years_exp, skill_items)
    resume_experience = _build_experience_string(companies, role_base, skill_items)

    # ── Skills string ─────────────────────────────────────────────────────────
    all_skills    = skill_items + soft
    resume_skills = ", ".join(all_skills)[:300]

    # ── Education string ──────────────────────────────────────────────────────
    cert = random.choice(CERTS) if random.random() > 0.4 else ""
    edu_parts = [f"{degree} – {uni_short}, {grad_year}"]
    if gpa_str:   edu_parts.append(gpa_str)
    edu_parts.append(f"TOEIC {toeic}")
    if cert:      edu_parts.append(f"Cert: {cert}")
    resume_education = " | ".join(edu_parts)[:256]

    resume_text = (
        f"Name: {name}\nEmail: {email} | Phone: {phone}\n"
        f"Location: {city}, Vietnam | LinkedIn: {linkedin}"
        + (f" | GitHub: {github}" if github else "") + "\n\n"
        f"OBJECTIVE\n{resume_summary}\n\n"
        f"EXPERIENCE\n{resume_experience}\n\n"
        f"SKILLS\n{resume_skills}\n\n"
        f"EDUCATION\n{resume_education}"
    )

    return {
        "resume_index":      index,
        "resume_summary":    resume_summary,
        "resume_experience": resume_experience,
        "resume_skills":     resume_skills,
        "resume_education":  resume_education,
        "resume_text":       resume_text,
        "_meta": {
            "name": name, "email": email, "phone": phone,
            "city": city, "linkedin": linkedin, "github": github,
            "role": full_role, "role_base": role_base,
            "years_exp": years_exp,
            "companies": companies,
            "uni_full": uni_full, "uni_short": uni_short,
            "degree": degree, "grad_year": grad_year,
            "gpa_str": gpa_str, "toeic": toeic,
            "skill_items": skill_items,
            "soft_skills": soft,
            "chosen_cats": chosen_cats,
            "cert": cert,
        },
    }


# ─────────────────────────────────────────────────────────────────────────────
# CSV DATA LOADING
# ─────────────────────────────────────────────────────────────────────────────

def load_csv_rows(csv_path: str) -> list[dict]:
    """Load all rows from the CV dataset CSV."""
    rows = []
    with open(csv_path, newline="", encoding="utf-8") as f:
        for row in _csv_module.DictReader(f):
            rows.append(row)
    return rows


def _parse_role_from_text(text: str) -> str:
    """Try to detect a job title from raw experience or summary text."""
    for role in [
        "Software Engineer", "Data Engineer", "Data Scientist",
        "Machine Learning Engineer", "DevOps Engineer",
        "Frontend Developer", "Front End Developer",
        "Backend Developer", "Full Stack Developer",
        "Data Analyst", "Mobile Engineer",
        "Site Reliability Engineer", "QA Engineer",
        "Security Engineer", "Technical Lead", "Staff Engineer",
    ]:
        if role.lower() in text.lower():
            return role
    return "Software Engineer"


def _parse_edu_from_csv(edu_text: str) -> tuple[str, str, str, int, str]:
    """Return (degree, uni_full, uni_short, grad_year, gpa_str)."""
    degree   = "Bachelor of Science"
    uni_full = "University"
    uni_short = "Univ"
    grad_year = 2020
    gpa_str   = ""

    if not edu_text:
        return degree, uni_full, uni_short, grad_year, gpa_str

    for kw in ["Bachelor", "Master", "PhD", "Doctor", "B.S.", "M.S.", "B.E.", "M.E."]:
        idx = edu_text.find(kw)
        if idx >= 0:
            degree = edu_text[idx:idx + 70].split(",")[0].split("\n")[0].strip()
            break

    gpa_m = re.search(r"GPA[:\s]+([0-9.]+)", edu_text, re.IGNORECASE)
    if gpa_m:
        gpa_str = f"GPA: {gpa_m.group(1)}"

    yr_m = re.search(r"\b(19|20)\d{2}\b", edu_text)
    if yr_m:
        grad_year = int(yr_m.group(0))

    return degree, uni_full, uni_short, grad_year, gpa_str


def generate_cv_data_from_csv(row: dict, index: int) -> dict:
    """Build CV data dict from a single CSV row — no faker dependency."""
    resume_summary    = (row.get("resume_summary")    or "")[:400]
    resume_experience = (row.get("resume_experience") or "")[:512]
    resume_skills     = (row.get("resume_skills")     or "")[:300]
    resume_education  = (row.get("resume_education")  or "")[:256]
    resume_text       =  row.get("resume_text")       or ""

    skill_items = [s.strip() for s in re.split(r"[,;]", resume_skills) if s.strip()][:15]

    degree, uni_full, uni_short, grad_year, gpa_str = _parse_edu_from_csv(resume_education)
    role_base = _parse_role_from_text(resume_experience or resume_summary)

    return {
        "resume_index":      index,
        "resume_summary":    resume_summary,
        "resume_experience": resume_experience,
        "resume_skills":     resume_skills,
        "resume_education":  resume_education,
        "resume_text":       resume_text,
        "_meta": {
            "name":        f"Candidate {index + 1}",
            "email":       f"candidate{index + 1}@example.com",
            "phone":       "—",
            "city":        "Vietnam",
            "linkedin":    "",
            "github":      "",
            "role":        role_base,
            "role_base":   role_base,
            "years_exp":   0,
            "companies":   [],
            "uni_full":    uni_full,
            "uni_short":   uni_short,
            "degree":      degree,
            "grad_year":   grad_year,
            "gpa_str":     gpa_str,
            "toeic":       0,
            "skill_items": skill_items,
            "soft_skills": [],
            "chosen_cats": [],
            "cert":        "",
        },
    }


# ─────────────────────────────────────────────────────────────────────────────
# HELPER: two-column row (date | detail)
# ─────────────────────────────────────────────────────────────────────────────
def _two_col(date_text: str, detail_flowables, styles: dict) -> Table:
    """Return a Table row: right-aligned date | left-aligned detail flowables."""
    date_para = Paragraph(date_text, styles["date"])
    tbl = Table(
        [[date_para, detail_flowables]],
        colWidths=[DATE_COL, DETAIL_COL],
        hAlign="LEFT",
    )
    tbl.setStyle(TableStyle([
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("TOPPADDING", (0, 0), (-1, -1), 0),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 0),
        ("LEFTPADDING", (0, 0), (0, 0), 0),
        ("LEFTPADDING", (1, 0), (1, 0), 8),
        ("RIGHTPADDING", (0, 0), (-1, -1), 0),
    ]))
    return tbl


def _section_rule(styles: dict):
    """Return [section header spacer, HR]."""
    return HRFlowable(
        width="100%", thickness=0.8,
        color=C_RULE, spaceAfter=5, spaceBefore=0,
        lineCap="round",
    )


# ─────────────────────────────────────────────────────────────────────────────
# PDF BUILDER – Harvard / Oxford single-column
# ─────────────────────────────────────────────────────────────────────────────
class HarvardCVRenderer:
    def __init__(self, data: dict, output_path: str, csv_mode: bool = False):
        self.data = data
        self.meta = data["_meta"]
        self.S = build_styles()
        self.output_path = output_path
        self.csv_mode = csv_mode
        random.seed(data["resume_index"] * 31 + 7)

    def _header(self) -> list:
        S, m = self.S, self.meta
        story = []
        story.append(Paragraph(m["name"].upper(), S["name"]))
        story.append(Paragraph(m["role"], S["tagline"]))
        contacts = "  ·  ".join(filter(None, [
            m["phone"], m["email"],
            m["city"] + ", Vietnam",
            m["linkedin"],
            m["github"],
        ]))
        story.append(Paragraph(contacts, S["contact"]))
        story.append(HRFlowable(width="100%", thickness=1.5, color=C_ACCENT,
                                 spaceAfter=4, spaceBefore=2))
        return story

    def _objective(self) -> list:
        S, d = self.S, self.data
        story = []
        story.append(Paragraph("OBJECTIVE", S["section"]))
        story.append(_section_rule(S))
        story.append(Paragraph(d["resume_summary"], S["body"]))
        return story

    def _education(self) -> list:
        S, m, d = self.S, self.meta, self.data
        story = []
        story.append(Paragraph("EDUCATION", S["section"]))
        story.append(_section_rule(S))

        # Primary degree
        end_year = m["grad_year"]
        start_year = end_year - 4
        date_str = f"{start_year} - {end_year}"

        detail = [
            Paragraph(m["uni_full"].upper(), S["job_title"]),
            Paragraph(m["degree"], S["job_org"]),
        ]
        if m["gpa_str"]:
            detail.append(Paragraph(m["gpa_str"], S["small_muted"]))
        detail.append(Paragraph(
            f"TOEIC: {m['toeic']}  ·  Relevant coursework: Data Structures & Algorithms, "
            f"Distributed Systems, Software Architecture, Database Management",
            S["small_muted"],
        ))

        story.append(_two_col(date_str, detail, S))
        story.append(Spacer(1, 4))

        # Cert if any
        if m["cert"]:
            story.append(_two_col(
                date.today().strftime("%Y"),
                [Paragraph(m["cert"], S["job_org"])],
                S,
            ))
            story.append(Spacer(1, 2))

        return story

    def _experience(self) -> list:
        S, m, d = self.S, self.meta, self.data
        story = []
        story.append(Paragraph("WORK EXPERIENCE", S["section"]))
        story.append(_section_rule(S))

        companies = m["companies"]
        role_base = m["role_base"]
        seniorities = ["Junior", "Mid-level", "Senior", "Lead", "Principal"]

        offset = 0
        for i, company in enumerate(companies):
            s, e = _date_range(
                min_months=8, max_months=36,
                end_offset_days=offset,
            )
            offset += random.randint(90, 400)
            sn = seniorities[min(i, len(seniorities) - 1)]
            team_size = random.randint(3, 15)
            stack_pick = random.sample(m["skill_items"], k=min(4, len(m["skill_items"])))
            stack_str = ", ".join(stack_pick)

            n_bullets = random.randint(3, 5)
            chosen_tmpls = random.sample(ACHIEVEMENT_TEMPLATES, k=n_bullets)
            bullets = [_bullet(t) for t in chosen_tmpls]

            detail = [
                Paragraph(f"{sn} {role_base}", S["job_title"]),
                Paragraph(
                    f"{company}  ·  Team size: {team_size}  ·  "
                    f"Tech: {stack_str}",
                    S["job_org"],
                ),
            ]
            for b in bullets:
                detail.append(Paragraph(f"- {b}", S["bullet"]))
            detail.append(Spacer(1, 4))

            story.append(KeepTogether(_two_col(f"{s} -\n{e}", detail, S)))

        return story

    def _skills(self) -> list:
        S, m, d = self.S, self.meta, self.data
        story = []
        story.append(Paragraph("TECHNICAL SKILLS", S["section"]))
        story.append(_section_rule(S))

        # Render by category
        for cat in m["chosen_cats"]:
            cat_skills = [s for s in m["skill_items"] if s in SKILLS_BY_CAT.get(cat, [])]
            if not cat_skills:
                continue
            row_detail = [Paragraph(", ".join(cat_skills), S["skills_val"])]
            story.append(KeepTogether(_two_col(cat, row_detail, S)))

        # Soft skills
        if m["soft_skills"]:
            story.append(KeepTogether(_two_col(
                "Soft Skills",
                [Paragraph(", ".join(m["soft_skills"]), S["skills_val"])],
                S,
            )))

        story.append(Spacer(1, 4))

        # Full skills string (model field value shown as note)
        story.append(Paragraph(
            f"Full model field value: {d['resume_skills']}",
            S["small_muted"],
        ))
        return story

    def _activities(self) -> list:
        S = self.S
        story = []
        story.append(Paragraph("ACTIVITIES & ACHIEVEMENTS", S["section"]))
        story.append(_section_rule(S))

        n = random.randint(2, 4)
        chosen = random.sample(ACTIVITIES, k=n)
        for act in chosen:
            year = date.today().year - random.randint(0, 3)
            story.append(KeepTogether(_two_col(
                str(year),
                [Paragraph(act, S["body"])],
                S,
            )))
            story.append(Spacer(1, 2))
        return story

    def _languages(self) -> list:
        S = self.S
        story = []
        story.append(Paragraph("LANGUAGES", S["section"]))
        story.append(_section_rule(S))

        lang_pick = [random.choice(LANGS)]
        if random.random() > 0.5:
            lang_pick.append(random.choice([l for l in LANGS if l[0] != lang_pick[0][0]]))

        for lang, prof in lang_pick:
            story.append(KeepTogether(_two_col(
                lang,
                [Paragraph(prof, S["skills_val"])],
                S,
            )))
        story.append(Spacer(1, 4))
        return story

    # ── CSV-mode section renderers ────────────────────────────────────────────

    def _experience_csv(self) -> list:
        S, d = self.S, self.data
        story = []
        story.append(Paragraph("WORK EXPERIENCE", S["section"]))
        story.append(_section_rule(S))
        text = d.get("resume_experience", "").strip()
        if text:
            for chunk in re.split(r";\s*|\n+", text):
                chunk = chunk.strip()
                if chunk:
                    story.append(Paragraph(f"- {chunk}", S["bullet"]))
        return story

    def _skills_csv(self) -> list:
        S, m, d = self.S, self.meta, self.data
        story = []
        story.append(Paragraph("TECHNICAL SKILLS", S["section"]))
        story.append(_section_rule(S))
        skill_items = m.get("skill_items", [])
        if skill_items:
            story.append(KeepTogether(_two_col(
                "Skills",
                [Paragraph(", ".join(skill_items), S["skills_val"])],
                S,
            )))
        elif d.get("resume_skills"):
            story.append(Paragraph(d["resume_skills"], S["body"]))
        story.append(Spacer(1, 4))
        return story

    def _education_csv(self) -> list:
        S, d = self.S, self.data
        story = []
        story.append(Paragraph("EDUCATION", S["section"]))
        story.append(_section_rule(S))
        text = d.get("resume_education", "").strip()
        if text:
            story.append(Paragraph(text, S["body"]))
        return story

    def build(self):
        doc = BaseDocTemplate(
            self.output_path,
            pagesize=A4,
            leftMargin=ML,
            rightMargin=MR,
            topMargin=MT,
            bottomMargin=MB,
        )

        def _on_page(canv, doc_):
            # Subtle page number
            canv.saveState()
            canv.setFont("Helvetica", 7)
            canv.setFillColor(C_MUTED)
            canv.drawRightString(
                PAGE_W - MR, MB / 2,
                f"{self.meta['name']}  ·  Page {doc_.page}",
            )
            canv.restoreState()

        frame = Frame(ML, MB, BODY_W, PAGE_H - MT - MB, id="body")
        pt = PageTemplate(id="main", frames=[frame], onPage=_on_page)
        doc.addPageTemplates([pt])

        if self.csv_mode:
            story = (
                self._header()
                + self._objective()
                + self._education_csv()
                + self._experience_csv()
                + self._skills_csv()
            )
        else:
            story = (
                self._header()
                + self._objective()
                + self._education()
                + self._experience()
                + self._skills()
                + self._activities()
                + self._languages()
            )

        doc.build(story)


# ─────────────────────────────────────────────────────────────────────────────
# MAIN
# ─────────────────────────────────────────────────────────────────────────────
# =============================================================================
# PDF EXTRACTION ENGINE
# Port of UploadCvStrategy.java section-alias matching logic.
# Reads raw PDF text -> maps to the 4 fields consumed by ResumeRequest.
# =============================================================================

# Mirror of UploadCvStrategy.SECTION_ALIASES (+ our Harvard template headers)
SECTION_ALIASES: dict[str, list[str]] = {
    "summary": [
        "objective", "career objective", "professional summary",
        "summary", "about me", "profile",
    ],
    "experience": [
        "experience", "work experience", "professional experience",
        "employment history", "work history",
    ],
    "skills": [
        "skills", "technical skills", "professional skills",
        "key skills", "core competencies", "technical & soft skills",
        "technical skills",
    ],
    "education": [
        "education", "academic background", "education & training",
        "qualifications",
    ],
    "languages": ["languages", "language skills", "languages known"],
    "activities": ["activities", "activities & achievements", "achievements", "extracurricular"],
    "certifications": ["certifications", "certification", "licenses", "credentials"],
}

# All known section header strings (used to detect section boundaries)
_ALL_HEADERS: set[str] = {
    h.lower() for headers in SECTION_ALIASES.values() for h in headers
}


def _is_section_header(line: str) -> bool:
    """Return True if line exactly matches any known section header."""
    return line.strip().lower() in _ALL_HEADERS


def _extract_section_lines(lines: list[str], section_key: str) -> list[str]:
    """
    Collect lines that belong to `section_key`, stopping when another
    known section header is encountered. Mirrors UploadCvStrategy.extractSection().
    """
    aliases = {a.lower() for a in SECTION_ALIASES.get(section_key, [section_key])}
    result: list[str] = []
    in_section = False

    for line in lines:
        stripped = line.strip()
        lower = stripped.lower()

        if lower in aliases:
            in_section = True
            continue

        if in_section and lower in _ALL_HEADERS:
            break

        if in_section and stripped:
            result.append(stripped)

    return result


def extract_fields_from_pdf(pdf_path: str) -> dict:
    """
    Extract the 4 resume fields from a PDF file using pdfplumber.
    Returns a dict with keys:
        resume_summary, resume_experience, resume_skills, resume_education
        resume_text  (full raw text)

    Logic mirrors UploadCvStrategy.parseCvText() in cv-service.
    """
    if _pdfplumber is None:
        raise ImportError("pdfplumber not installed. Run: pip install pdfplumber")

    full_text_pages = []
    with _pdfplumber.open(pdf_path) as pdf:
        for page in pdf.pages:
            t = page.extract_text()
            if t:
                full_text_pages.append(t)

    full_text = "\n".join(full_text_pages)

    lines = [
        l.strip()
        for l in full_text.split("\n")
        if l.strip()
    ]

    summary_lines    = _extract_section_lines(lines, "summary")
    experience_lines = _extract_section_lines(lines, "experience")
    skills_lines     = _extract_section_lines(lines, "skills")
    education_lines  = _extract_section_lines(lines, "education")

    def join_truncate(parts: list[str], limit: int) -> str:
        return " ".join(parts)[:limit]

    return {
        "resume_summary":    join_truncate(summary_lines,    400),
        "resume_experience": join_truncate(experience_lines, 512),
        "resume_skills":     join_truncate(skills_lines,     300),
        "resume_education":  join_truncate(education_lines,  256),
        "resume_text":       full_text,
        # diagnostics
        "_lines_found": {
            "summary":    len(summary_lines),
            "experience": len(experience_lines),
            "skills":     len(skills_lines),
            "education":  len(education_lines),
        },
    }


def extract_mode(pdf_paths: list[str], out_dir: Path, verbose: bool = False) -> None:
    """
    Extract fields from one or more PDF files and write companion JSON files.
    Also prints a comparison table if the original .json exists beside the PDF.
    """
    if _pdfplumber is None:
        print("ERROR: pdfplumber is required for --extract.")
        print("       Run: pip install pdfplumber")
        sys.exit(1)

    out_dir.mkdir(parents=True, exist_ok=True)
    results = []

    for idx, pdf_path in enumerate(pdf_paths):
        p = Path(pdf_path)
        if not p.exists():
            print(f"  [SKIP] not found: {pdf_path}")
            continue

        print(f"  Extracting [{idx+1}/{len(pdf_paths)}] {p.name} ...", end=" ", flush=True)

        try:
            extracted = extract_fields_from_pdf(str(p))
        except Exception as e:
            print(f"ERROR: {e}")
            continue

        found = extracted["_lines_found"]
        print(
            f"summary={found['summary']}L  "
            f"exp={found['experience']}L  "
            f"skills={found['skills']}L  "
            f"edu={found['education']}L"
        )

        # Write extracted JSON
        stem = p.stem
        out_json = out_dir / f"{stem}.extracted.json"
        payload = {
            "pdf_file":          p.name,
            "resume_index":      idx,
            "resume_summary":    extracted["resume_summary"],
            "resume_experience": extracted["resume_experience"],
            "resume_skills":     extracted["resume_skills"],
            "resume_education":  extracted["resume_education"],
            "resume_text":       extracted["resume_text"],
        }
        with open(out_json, "w", encoding="utf-8") as f:
            json.dump(payload, f, ensure_ascii=False, indent=2)

        # Optional diff vs original generated JSON
        orig_json = p.parent / f"{stem}.json"
        if orig_json.exists() and verbose:
            with open(orig_json, encoding="utf-8") as f:
                original = json.load(f)

            print(f"\n  --- Diff vs generated JSON for {p.name} ---")
            for field in ("resume_summary", "resume_experience", "resume_skills", "resume_education"):
                gen_val  = original.get(field, "")[:80]
                ext_val  = extracted.get(field, "")[:80]
                match    = "OK" if gen_val.strip() == ext_val.strip() else "DIFF"
                print(f"  [{match}] {field}")
                if match == "DIFF":
                    print(f"       GEN: {gen_val!r}")
                    print(f"       EXT: {ext_val!r}")
            print()

        results.append(payload)

    # Write extraction manifest
    manifest_path = out_dir / "extracted_manifest.json"
    with open(manifest_path, "w", encoding="utf-8") as f:
        json.dump({"total": len(results), "records": results}, f, ensure_ascii=False, indent=2)

    print(f"\n✅  Extraction done!")
    print(f"   Extracted JSON : {out_dir.resolve()}/*.extracted.json")
    print(f"   Manifest       : {manifest_path.resolve()}")
    print("\n   Drop any .extracted.json into CvRankRequest.resumes[] to test the model.")


# =============================================================================
# ARG PARSING & MAIN
# =============================================================================
import textwrap


def parse_args():
    p = argparse.ArgumentParser(
        description="Generate Harvard-style CV PDFs and/or extract fields for WorkFitAI.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=textwrap.dedent("""
        Modes:
          generate (default)  Generate N synthetic CV PDFs (faker-based).
          --job-json FILE     Generate N CVs biased toward a specific job (integration test).
          --csv FILE          Generate CVs from real data in a CSV file.
          --extract FILE...   Extract 4 model fields from existing PDF file(s).

        Examples:
          python generate_cv_pdfs.py --count 100
          python generate_cv_pdfs.py --job-json job_001.json --count 100 --output output/cvs/job_001
          python generate_cv_pdfs.py --job-title "Software Engineer" --job-skills "Python,FastAPI,Docker" --count 100
          python generate_cv_pdfs.py --csv output/cvs/test.csv --csv-row 42
          python generate_cv_pdfs.py --extract output/cvs/cv_0000_*.pdf
        """),
    )
    # generate mode options
    p.add_argument("--count",         type=int,   default=20,           help="Number of CVs to generate (default: 20; job mode default: 100)")
    p.add_argument("--output",        type=str,   default="output/cvs", help="Output directory")
    p.add_argument("--seed",          type=int,   default=42,           help="Random seed (default: 42)")
    p.add_argument("--no-json",       action="store_true",              help="Skip companion JSON files")
    # job mode options
    p.add_argument("--job-json",      type=str,   default=None,         help="Path to a job spec JSON file (activates job mode)")
    p.add_argument("--job-title",     type=str,   default=None,         help="Job title (activates job mode when provided)")
    p.add_argument("--job-skills",    type=str,   default=None,         help="Comma-separated required skills for the job")
    p.add_argument("--job-level",     type=str,   default=None,         help="Seniority level: junior | mid | senior")
    p.add_argument("--job-mix-rate",  type=float, default=0.7,          help="Fraction of CVs with matching role (default: 0.7)")
    # csv mode options
    p.add_argument("--csv",           type=str,   default=None,         help="CSV file to use as data source (replaces faker)")
    p.add_argument("--csv-row",       type=int,   default=None,         help="Generate only this 0-based row index from the CSV")
    # extract mode
    p.add_argument("--extract",       nargs="+",  metavar="PDF",        help="Extract fields from PDF file(s) instead of generating")
    p.add_argument("--extract-out",   type=str,   default=None,         help="Output dir for extracted JSON (default: same dir as PDFs)")
    p.add_argument("--verbose",       action="store_true",              help="Print diff vs original JSON when extracting")
    # batch mode
    p.add_argument("--batch-jobs",    type=str,   default=None,         help="Path to jobs.json (activates batch mode)")
    p.add_argument("--batch-apps",    type=str,   default=None,         help="Path to applications.json (used with --batch-jobs)")
    return p.parse_args()


def main():
    args = parse_args()

    # ── EXTRACT MODE ──────────────────────────────────────────────────────────
    if args.extract:
        import glob as _glob

        pdf_list: list[str] = []
        for pattern in args.extract:
            matches = sorted(_glob.glob(pattern))
            if matches:
                pdf_list.extend(matches)
            elif Path(pattern).exists():
                pdf_list.append(pattern)
            else:
                print(f"  [WARN] No file(s) matched: {pattern}")

        if not pdf_list:
            print("ERROR: No PDF files found.")
            sys.exit(1)

        out_dir = Path(args.extract_out) if args.extract_out else Path(pdf_list[0]).parent
        print(f"\nExtracting fields from {len(pdf_list)} PDF(s) -> {out_dir.resolve()}\n")
        extract_mode(pdf_list, out_dir, verbose=args.verbose)
        return

    # ── BATCH MODE ────────────────────────────────────────────────────────────
    if args.batch_jobs:
        jobs_path = Path(args.batch_jobs)
        apps_path = Path(args.batch_apps) if args.batch_apps else Path("applications.json")

        if not jobs_path.exists():
            print(f"ERROR: jobs file not found: {jobs_path}")
            sys.exit(1)
        if not apps_path.exists():
            print(f"ERROR: applications file not found: {apps_path}")
            sys.exit(1)

        with open(jobs_path, encoding="utf-8") as f:
            all_jobs = json.load(f)["jobs"]
        with open(apps_path, encoding="utf-8") as f:
            applications = json.load(f)["applications"]

        # Build job lookup: {hrmKey: [job0, job1, ...]}
        job_lookup: dict[str, list[dict]] = {}
        for j in all_jobs:
            job_lookup.setdefault(j["hrmKey"], []).append(j)

        # Determine unique job keys in first-seen order → assign 1-based job_idx
        seen_job_keys: dict[tuple, int] = {}
        for app in applications:
            key = (app["jobRef"]["hrmKey"], app["jobRef"]["index"])
            if key not in seen_job_keys:
                seen_job_keys[key] = len(seen_job_keys) + 1

        # Group candidates per job key (preserving order)
        job_candidates: dict[tuple, list[int]] = {}
        for app in applications:
            key = (app["jobRef"]["hrmKey"], app["jobRef"]["index"])
            job_candidates.setdefault(key, []).append(app["candidateNum"])

        out_dir = Path(args.output) if args.output != "output/cvs" else Path("output/cvs/results")
        out_dir.mkdir(parents=True, exist_ok=True)

        print(f"Batch mode: {len(seen_job_keys)} jobs, {len(applications)} applications -> {out_dir.resolve()}")

        Faker.seed(args.seed)
        random.seed(args.seed)

        manifest = []
        for job_key, candidates in job_candidates.items():
            hrm_key, job_index = job_key
            job_idx = seen_job_keys[job_key]

            # Resolve job spec from lookup
            hrm_jobs = job_lookup.get(hrm_key, [])
            if job_index >= len(hrm_jobs):
                print(f"  [WARN] jobRef {hrm_key}/{job_index} out of range, skipping")
                continue
            raw_job = hrm_jobs[job_index]
            job_spec = _parse_job_spec(raw_job)

            print(f"\n  Job {job_idx}: [{hrm_key}/{job_index}] {job_spec['title']}")

            for cand_ordinal, cand_num in enumerate(candidates, start=1):
                data = generate_cv_data_for_job(cand_num, job_spec)
                meta = data["_meta"]

                basename = f"candidate{job_idx}_{cand_ordinal}"
                pdf_path  = str(out_dir / f"{basename}.pdf")
                json_path = str(out_dir / f"{basename}.json")

                HarvardCVRenderer(data, pdf_path).build()

                if not args.no_json:
                    payload = {
                        "candidateNum":      cand_num,
                        "jobRef":            {"hrmKey": hrm_key, "index": job_index},
                        "jobTitle":          job_spec["title"],
                        "resume_index":      cand_num,
                        "resume_summary":    data["resume_summary"],
                        "resume_experience": data["resume_experience"],
                        "resume_skills":     data["resume_skills"],
                        "resume_education":  data["resume_education"],
                        "resume_text":       data["resume_text"],
                        "pdf_file":          f"{basename}.pdf",
                    }
                    with open(json_path, "w", encoding="utf-8") as f:
                        json.dump(payload, f, ensure_ascii=False, indent=2)

                manifest.append({
                    "file":         f"{basename}.pdf",
                    "candidateNum": cand_num,
                    "name":         meta["name"],
                    "role":         meta["role"],
                    "job_idx":      job_idx,
                    "jobRef":       {"hrmKey": hrm_key, "index": job_index},
                    "jobTitle":     job_spec["title"],
                })
                print(f"    [{cand_ordinal}/{len(candidates)}] {basename}.pdf  ({meta['name']})")

        manifest_path = out_dir / "manifest.json"
        with open(manifest_path, "w", encoding="utf-8") as f:
            json.dump({"total": len(manifest), "records": manifest}, f, ensure_ascii=False, indent=2)

        print(f"\nDone! (batch mode)")
        print(f"   Output   : {out_dir.resolve()}")
        print(f"   Manifest : {manifest_path.resolve()}")
        return

    # ── JOB MODE ──────────────────────────────────────────────────────────────
    if args.job_json or args.job_title:
        if args.job_json:
            spec_source = args.job_json
            if not Path(spec_source).exists():
                print(f"ERROR: job spec file not found: {spec_source}")
                sys.exit(1)
        else:
            spec_source = args  # parse from --job-title / --job-skills etc.
        job_spec = _parse_job_spec(spec_source)

        if not job_spec["title"] and not job_spec["skills"]:
            print("ERROR: job mode requires --job-title or --job-json with a title/skills field.")
            sys.exit(1)

        # default count 100 for job mode if user didn't explicitly pass --count
        total = args.count if args.count != 20 else 100

        out_dir = Path(args.output)
        out_dir.mkdir(parents=True, exist_ok=True)

        safe_title = re.sub(r"[^a-z0-9]+", "_", job_spec["title"].lower()).strip("_")
        job_id     = job_spec["job_id"] or safe_title or "job"

        print(f"Job mode: '{job_spec['title']}' | skills={len(job_spec['skills'])} | mix={job_spec['mix_rate']}")
        print(f"Generating {total} CV PDF(s) -> {out_dir.resolve()}")

        Faker.seed(args.seed)
        random.seed(args.seed)

        manifest = []
        for i in range(total):
            data = generate_cv_data_for_job(i, job_spec)
            meta = data["_meta"]

            safe = meta["name"].lower().replace(" ", "_").replace(".", "").replace(",", "")
            basename = f"cv_{i:04d}_{safe}"

            pdf_path  = str(out_dir / f"{basename}.pdf")
            json_path = str(out_dir / f"{basename}.json")

            HarvardCVRenderer(data, pdf_path).build()

            if not args.no_json:
                payload = {
                    # ── Fields for POST / (application-service) ──────────────
                    "jobId":             job_id,          # → CreateApplicationRequest.jobId
                    "email":             meta["email"],   # → CreateApplicationRequest.email
                    "pdf_file":          f"{basename}.pdf",  # upload as cvPdfFile
                    # ── Fields for CvRankRequest.resumes[] ───────────────────
                    "resume_index":      i,
                    "resume_summary":    data["resume_summary"],
                    "resume_experience": data["resume_experience"],
                    "resume_skills":     data["resume_skills"],
                    "resume_education":  data["resume_education"],
                    "resume_text":       data["resume_text"],
                }
                with open(json_path, "w", encoding="utf-8") as f:
                    json.dump(payload, f, ensure_ascii=False, indent=2)

            manifest.append({
                "resume_index": i,
                "jobId":        job_id,
                "email":        meta["email"],
                "name":         meta["name"],
                "role":         meta["role"],
                "years_exp":    meta["years_exp"],
                "pdf":          f"{basename}.pdf",
                "json":         f"{basename}.json" if not args.no_json else None,
            })

            pct = (i + 1) / total * 100
            bar = "#" * int(pct / 5) + "." * (20 - int(pct / 5))
            print(f"\r  [{bar}] {i+1:>{len(str(total))}}/{total}  {basename}", end="", flush=True)

        print()
        manifest_path = out_dir / "manifest.json"
        with open(manifest_path, "w", encoding="utf-8") as f:
            json.dump({"total": total, "job_id": job_id,
                       "job_spec": {k: v for k, v in job_spec.items() if k != "skills"},
                       "records": manifest}, f, ensure_ascii=False, indent=2)

        print(f"\nDone! (job mode)")
        print(f"   Job      : {job_spec['title'] or '(skills only)'}")
        print(f"   PDFs     : {out_dir.resolve()}/cv_*.pdf")
        if not args.no_json:
            print(f"   JSON     : {out_dir.resolve()}/cv_*.json")
        print(f"   Manifest : {manifest_path.resolve()}")
        print(f"\n   Drop any .json into CvRankRequest.resumes[] to test the ranking model.")
        return

    # ── CSV MODE ──────────────────────────────────────────────────────────────
    if args.csv:
        csv_path = Path(args.csv)
        if not csv_path.exists():
            print(f"ERROR: CSV file not found: {csv_path}")
            sys.exit(1)

        rows = load_csv_rows(str(csv_path))

        if args.csv_row is not None:
            if args.csv_row >= len(rows):
                print(f"ERROR: --csv-row {args.csv_row} out of range (CSV has {len(rows)} rows)")
                sys.exit(1)
            rows_to_gen = [(args.csv_row, rows[args.csv_row])]
        else:
            total_csv = min(args.count, len(rows))
            rows_to_gen = list(enumerate(rows[:total_csv]))

        out_dir = Path(args.output)
        out_dir.mkdir(parents=True, exist_ok=True)

        total = len(rows_to_gen)
        print(f"Generating {total} CV PDF(s) from CSV -> {out_dir.resolve()}")

        manifest = []
        for i, (row_idx, row) in enumerate(rows_to_gen):
            data     = generate_cv_data_from_csv(row, row_idx)
            basename = f"cv_{row_idx:04d}_candidate_{row_idx + 1}"
            pdf_path  = str(out_dir / f"{basename}.pdf")
            json_path = str(out_dir / f"{basename}.json")

            HarvardCVRenderer(data, pdf_path, csv_mode=True).build()

            if not args.no_json:
                payload = {
                    "resume_index":      row_idx,
                    "resume_summary":    data["resume_summary"],
                    "resume_experience": data["resume_experience"],
                    "resume_skills":     data["resume_skills"],
                    "resume_education":  data["resume_education"],
                    "resume_text":       data["resume_text"],
                    "pdf_file":          f"{basename}.pdf",
                }
                with open(json_path, "w", encoding="utf-8") as f:
                    json.dump(payload, f, ensure_ascii=False, indent=2)

            manifest.append({
                "resume_index": row_idx,
                "name":  data["_meta"]["name"],
                "role":  data["_meta"]["role"],
                "pdf":   f"{basename}.pdf",
                "json":  f"{basename}.json" if not args.no_json else None,
            })

            pct = (i + 1) / total * 100
            bar = "#" * int(pct / 5) + "." * (20 - int(pct / 5))
            print(f"\r  [{bar}] {i+1:>{len(str(total))}}/{total}  {basename}", end="", flush=True)

        print()

        manifest_path = out_dir / "manifest.json"
        with open(manifest_path, "w", encoding="utf-8") as f:
            json.dump({"total": total, "records": manifest}, f, ensure_ascii=False, indent=2)

        print(f"\nDone! (CSV mode)")
        print(f"   PDFs     : {out_dir.resolve()}/cv_*.pdf")
        if not args.no_json:
            print(f"   JSON     : {out_dir.resolve()}/cv_*.json")
        print(f"   Manifest : {manifest_path.resolve()}")
        return

    # ── GENERATE MODE ─────────────────────────────────────────────────────────
    Faker.seed(args.seed)
    random.seed(args.seed)

    out_dir = Path(args.output)
    out_dir.mkdir(parents=True, exist_ok=True)

    manifest = []
    total = args.count
    print(f"Generating {total} Harvard-style CV PDF(s) -> {out_dir.resolve()}")

    for i in range(total):
        data = generate_cv_data(i)
        meta = data["_meta"]

        safe = meta["name"].lower().replace(" ", "_").replace(".", "").replace(",", "")
        basename = f"cv_{i:04d}_{safe}"

        pdf_path  = str(out_dir / f"{basename}.pdf")
        json_path = str(out_dir / f"{basename}.json")

        HarvardCVRenderer(data, pdf_path).build()

        if not args.no_json:
            payload = {
                "resume_index":      data["resume_index"],
                "resume_summary":    data["resume_summary"],
                "resume_experience": data["resume_experience"],
                "resume_skills":     data["resume_skills"],
                "resume_education":  data["resume_education"],
                "resume_text":       data["resume_text"],
                "pdf_file": f"{basename}.pdf",
            }
            with open(json_path, "w", encoding="utf-8") as f:
                json.dump(payload, f, ensure_ascii=False, indent=2)

        manifest.append({
            "resume_index": i,
            "name": meta["name"],
            "role": meta["role"],
            "years_exp": meta["years_exp"],
            "pdf":  f"{basename}.pdf",
            "json": f"{basename}.json" if not args.no_json else None,
        })

        pct = (i + 1) / total * 100
        bar = "#" * int(pct / 5) + "." * (20 - int(pct / 5))
        print(f"\r  [{bar}] {i+1:>{len(str(total))}}/{total}  {basename}", end="", flush=True)

    print()

    manifest_path = out_dir / "manifest.json"
    with open(manifest_path, "w", encoding="utf-8") as f:
        json.dump({"total": total, "records": manifest}, f, ensure_ascii=False, indent=2)

    print(f"\nDone!")
    print(f"   PDFs     : {out_dir.resolve()}/cv_*.pdf")
    print(f"   JSON     : {out_dir.resolve()}/cv_*.json")
    print(f"   Manifest : {manifest_path.resolve()}")
    print("\n   Each .json is ready to drop into CvRankRequest.resumes[]")


if __name__ == "__main__":
    main()

