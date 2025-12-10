-- V001__init_schema.sql
-- Initialize job-service database schema

-- Create companies table
CREATE TABLE companies
(
    company_no         VARCHAR(255) PRIMARY KEY,
    name               VARCHAR(255) NOT NULL,
    logo_url           VARCHAR(500),
    website_url        VARCHAR(500),
    description        TEXT,
    address            TEXT,
    size               VARCHAR(50),
    created_by         VARCHAR(50),
    created_date       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_modified_by   VARCHAR(50),
    last_modified_date TIMESTAMP
);

-- Create skills table
CREATE TABLE skills
(
    skill_id           UUID PRIMARY KEY,
    name               VARCHAR(255) NOT NULL UNIQUE,
    created_by         VARCHAR(50),
    created_date       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_modified_by   VARCHAR(50),
    last_modified_date TIMESTAMP
);

-- Create jobs table
CREATE TABLE jobs
(
    job_id              UUID PRIMARY KEY,
    title               VARCHAR(120)   NOT NULL,
    description         TEXT           NOT NULL,
    short_description   VARCHAR(500)   NOT NULL,

    employment_type     VARCHAR(50)    NOT NULL,
    experience_level    VARCHAR(50)    NOT NULL,
    required_experience VARCHAR(120),

    salary_min          DECIMAL(15, 2) NOT NULL,
    salary_max          DECIMAL(15, 2) NOT NULL,
    currency            VARCHAR(3)     NOT NULL,

    location            VARCHAR(255)   NOT NULL,
    quantity            INT            NOT NULL DEFAULT 1,
    total_applications  INT            NOT NULL DEFAULT 0,

    expires_at          TIMESTAMP      NOT NULL,
    status              VARCHAR(50)    NOT NULL,

    education_level     VARCHAR(120),

    benefits            TEXT,
    requirements        TEXT,
    responsibilities    TEXT,

    featured            BOOLEAN        NOT NULL DEFAULT FALSE,
    views               BIGINT         NOT NULL DEFAULT 0,
    banner_url          VARCHAR(500)
    company_id          VARCHAR(255)   NOT NULL REFERENCES companies (company_no) ON DELETE CASCADE,

    created_by          VARCHAR(50),
    created_date        TIMESTAMP               DEFAULT CURRENT_TIMESTAMP,
    last_modified_by    VARCHAR(50),
    last_modified_date  TIMESTAMP
);

-- Create job_skill junction table
CREATE TABLE job_skill
(
    job_id   UUID NOT NULL REFERENCES jobs (job_id) ON DELETE CASCADE,
    skill_id UUID NOT NULL REFERENCES skills (skill_id) ON DELETE CASCADE,
    PRIMARY KEY (job_id, skill_id)
);

-- Create subscribers table
CREATE TABLE subscribers
(
    subscriber_id      BIGSERIAL PRIMARY KEY,
    email              VARCHAR(255) NOT NULL,
    name               VARCHAR(255) NOT NULL,
    created_by         VARCHAR(50),
    created_date       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_modified_by   VARCHAR(50),
    last_modified_date TIMESTAMP
);

-- Create subscriber_skill junction table
CREATE TABLE subscriber_skill
(
    subscriber_id BIGINT NOT NULL REFERENCES subscribers (subscriber_id) ON DELETE CASCADE,
    skill_id      UUID   NOT NULL REFERENCES skills (skill_id) ON DELETE CASCADE,
    PRIMARY KEY (subscriber_id, skill_id)
);

-- Indexes
CREATE INDEX idx_jobs_company ON jobs (company_id);
CREATE INDEX idx_jobs_status ON jobs (status);
CREATE INDEX idx_jobs_expires ON jobs (expires_at);
CREATE INDEX idx_jobs_featured ON jobs (featured);
CREATE INDEX idx_jobs_views ON jobs (views);

CREATE INDEX idx_subscribers_email ON subscribers (email);
