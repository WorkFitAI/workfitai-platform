-- EXTENSIONS

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- COMPANIES

INSERT INTO companies (company_no, name, description, address, logo_url)
VALUES
('0101248141_FPT', 'FPT', 'IT company', 'Hà Nội, Việt Nam', 'https://img.example.com/fpt.png'),
('0309807306_KMS', 'KMS Technology', 'Software company', 'HCM, Việt Nam', 'https://img.example.com/kms.png'),
('0303881597_VNG', 'VNG', 'Tech company', 'Hà Nội, Việt Nam', 'https://img.example.com/vng.png'),
('0302731081_TMA', 'TMA Solutions', 'Outsourcing company', 'Đà Nẵng, Việt Nam', 'https://img.example.com/tma.png'),
('0100109106_VNPT', 'VNPT', 'Telecom company', 'Hà Nội, Việt Nam', 'https://img.example.com/vnpt.png');

-- SKILLS

INSERT INTO skills (skill_id, name)
VALUES
(gen_random_uuid(), 'Java'),
(gen_random_uuid(), 'Spring Boot'),
(gen_random_uuid(), 'Hibernate'),
(gen_random_uuid(), 'JPA'),
(gen_random_uuid(), 'MySQL'),
(gen_random_uuid(), 'PostgreSQL'),
(gen_random_uuid(), 'MongoDB'),
(gen_random_uuid(), 'Redis'),
(gen_random_uuid(), 'Docker'),
(gen_random_uuid(), 'Kubernetes'),

(gen_random_uuid(), 'AWS'),
(gen_random_uuid(), 'Azure'),
(gen_random_uuid(), 'GCP'),
(gen_random_uuid(), 'Linux'),
(gen_random_uuid(), 'Git'),

(gen_random_uuid(), 'CI/CD'),
(gen_random_uuid(), 'Jenkins'),
(gen_random_uuid(), 'GitHub Actions'),
(gen_random_uuid(), 'Microservices'),
(gen_random_uuid(), 'REST API'),

(gen_random_uuid(), 'GraphQL'),
(gen_random_uuid(), 'RabbitMQ'),
(gen_random_uuid(), 'Kafka'),
(gen_random_uuid(), 'System Design'),
(gen_random_uuid(), 'Data Structures'),

(gen_random_uuid(), 'Algorithms'),
(gen_random_uuid(), 'OOP'),
(gen_random_uuid(), 'Design Patterns'),
(gen_random_uuid(), 'Clean Code'),
(gen_random_uuid(), 'SOLID Principles'),

(gen_random_uuid(), 'ReactJS'),
(gen_random_uuid(), 'NextJS'),
(gen_random_uuid(), 'Angular'),
(gen_random_uuid(), 'VueJS'),
(gen_random_uuid(), 'TypeScript'),

(gen_random_uuid(), 'NodeJS'),
(gen_random_uuid(), 'ExpressJS'),
(gen_random_uuid(), 'NestJS'),

(gen_random_uuid(), 'Python'),
(gen_random_uuid(), 'Django'),
(gen_random_uuid(), 'Flask'),

(gen_random_uuid(), '.NET'),
(gen_random_uuid(), 'C#'),

(gen_random_uuid(), 'Android'),
(gen_random_uuid(), 'Kotlin'),
(gen_random_uuid(), 'Swift'),

(gen_random_uuid(), 'HTML'),
(gen_random_uuid(), 'CSS'),
(gen_random_uuid(), 'TailwindCSS'),
(gen_random_uuid(), 'UI/UX Design');

-- JOBS (~50 jobs)

DO $$
DECLARE i INT := 1;
DECLARE comp TEXT[];
BEGIN
comp := ARRAY[
'0101248141_FPT',
'0309807306_KMS',
'0303881597_VNG',
'0302731081_TMA',
'0100109106_VNPT'
];

WHILE i <= 1000 LOOP
INSERT INTO jobs (
    job_id,
    title,
    description,
    short_description,
    employment_type,
    experience_level,
    required_experience,
    salary_min,
    salary_max,
    views,
    currency,
    location,
    quantity,
    total_applications,
    expires_at,
    status,
    education_level,
    benefits,
    requirements,
    responsibilities,
    company_id,
    is_deleted
)
VALUES (
    gen_random_uuid(),

    CASE
        WHEN i % 3 = 0 THEN 'Senior Backend Dev #' || i
        WHEN i % 3 = 1 THEN 'Mid Frontend Dev #' || i
        ELSE 'Junior Fullstack Dev #' || i
    END,

    'This is a detailed job description for job #' || i || 
    ', responsible for system development, maintenance and optimization.',

    'Short description ' || i,

    CASE WHEN i % 2 = 0 THEN 'FULL_TIME' ELSE 'PART_TIME' END,

    CASE
        WHEN i % 3 = 0 THEN 'SENIOR'
        WHEN i % 3 = 1 THEN 'MID'
        ELSE 'JUNIOR'
    END,

    '2-4 years',

    1000 + i * 10,
    2000 + i * 20,

    (random() * 1000)::int,

    'USD',

    'Vietnam',

    1 + (random() * 5)::int,

    0,

    NOW() + INTERVAL '30 days',

    'PUBLISHED',

    'University',

    'Good salary and career growth opportunity',

    'Coding, teamwork, problem solving',

    'Build scalable systems and APIs',

    comp[(i % array_length(comp,1)) + 1],

    false
);

i := i + 1;
END LOOP;
END $$;

-- JOB_SKILL

INSERT INTO job_skill (job_id, skill_id)
SELECT j.job_id, s.skill_id
FROM jobs j
CROSS JOIN skills s
WHERE random() < 0.25;

-- REPORTS (~20)

INSERT INTO reports (report_id, report_content, status, job_id)
SELECT
    gen_random_uuid(),
    'Report for job: ' || j.title,
    'PENDING',
    j.job_id
FROM jobs j
LIMIT 20;