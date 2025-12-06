-- V001__init_schema.sql
-- Initialize user-service database schema

-- Create companies table first (referenced by hr_profiles)
CREATE TABLE companies (
    id UUID PRIMARY KEY,
    company_name VARCHAR(255) NOT NULL,
    industry VARCHAR(100),
    company_size VARCHAR(50),
    website VARCHAR(255),
    address VARCHAR(500),
    created_by VARCHAR(50),
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_by VARCHAR(50),
    last_modified_date TIMESTAMP,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

-- Create users table (base table for inheritance)
CREATE TABLE users (
    user_id UUID PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    username VARCHAR(50) NOT NULL UNIQUE,
    phone_number VARCHAR(20) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    user_role VARCHAR(50) NOT NULL,
    user_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    last_login TIMESTAMP,
    created_by VARCHAR(50),
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_by VARCHAR(50),
    last_modified_date TIMESTAMP,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

-- Create admins table (extends users)
CREATE TABLE admins (
    user_id UUID PRIMARY KEY REFERENCES users (user_id) ON DELETE CASCADE
);

-- Create hr_profiles table (extends users)
CREATE TABLE hr_profiles (
    user_id UUID PRIMARY KEY REFERENCES users (user_id) ON DELETE CASCADE,
    department VARCHAR(255) NOT NULL,
    company_id UUID NOT NULL,
    address VARCHAR(255) NOT NULL,
    approved_by VARCHAR(255),
    approved_at TIMESTAMP
);

-- Create candidates table (extends users)
CREATE TABLE candidates (
    user_id UUID PRIMARY KEY REFERENCES users (user_id) ON DELETE CASCADE,
    career_objective TEXT,
    summary TEXT,
    total_experience INT DEFAULT 0,
    education TEXT,
    certifications TEXT,
    portfolio_link VARCHAR(500),
    linkedin_url VARCHAR(500),
    github_url VARCHAR(500),
    expected_position VARCHAR(255)
);

-- Create candidate_cv_refs table (for CV references)
CREATE TABLE candidate_cv_refs (
    candidate_id UUID NOT NULL REFERENCES candidates (user_id) ON DELETE CASCADE,
    cv_id UUID NOT NULL
);

-- Create candidate_profile_skills table
CREATE TABLE candidate_profile_skills (
    id UUID PRIMARY KEY,
    candidate_id UUID NOT NULL REFERENCES candidates (user_id) ON DELETE CASCADE,
    skill_name VARCHAR(255) NOT NULL,
    skill_level VARCHAR(50),
    years_experience INT DEFAULT 0
);

-- Create indexes for better performance
CREATE INDEX idx_users_email ON users (email);

CREATE INDEX idx_users_username ON users (username);

CREATE INDEX idx_users_role ON users (user_role);

CREATE INDEX idx_users_status ON users (user_status);

CREATE INDEX idx_hr_company ON hr_profiles (company_id);

CREATE INDEX idx_candidate_cv_refs ON candidate_cv_refs (candidate_id);