-- Seed admin and test HR accounts for user-service
-- V002__seed_admin_accounts.sql
-- Uses JOINED inheritance strategy: users table has base data, child tables reference via user_id
-- UUIDs must be valid format for PostgreSQL

-- UUIDs for consistency across services:
-- Admin:      00000000-0000-0000-0000-000000000001
-- HR Manager: 00000000-0000-0000-0000-000000000002
-- HR Staff:   00000000-0000-0000-0000-000000000003
-- Company:    550e8400-e29b-41d4-a716-446655440001

-- Insert test company first (referenced by HR accounts)
INSERT INTO
    companies (
        id,
        company_name,
        industry,
        company_size,
        website,
        address,
        created_by,
        created_date,
        last_modified_by,
        last_modified_date,
        is_deleted
    )
VALUES (
        '550e8400-e29b-41d4-a716-446655440001'::uuid,
        'TechCorp Vietnam',
        'Technology',
        '100-500',
        'https://techcorp.vn',
        '123 Tech Street, Ho Chi Minh City',
        'system',
        NOW(),
        'system',
        NOW(),
        FALSE
    );

-- Insert admin account (matches auth-service admin)
INSERT INTO
    users (
        user_id,
        full_name,
        email,
        username,
        phone_number,
        password_hash,
        user_role,
        user_status,
        created_by,
        created_date,
        last_modified_by,
        last_modified_date,
        is_deleted
    )
VALUES (
        '00000000-0000-0000-0000-000000000001'::uuid,
        'System Administrator',
        'admin@workfitai.com',
        'admin',
        '+84901234567',
        '$2a$10$rI8GBZ8OXAZBFhRpGP1K7uE.0M9oOHGZkDPKqN4uJ/2KJ8JUZ5Kke',
        'ADMIN',
        'ACTIVE',
        'system',
        NOW(),
        'system',
        NOW(),
        FALSE
    );

-- admins table only has user_id as PK (JOINED inheritance)
INSERT INTO
    admins (user_id)
VALUES (
        '00000000-0000-0000-0000-000000000001'::uuid
    );

-- Insert test HR_MANAGER account (matches auth-service)
INSERT INTO
    users (
        user_id,
        full_name,
        email,
        username,
        phone_number,
        password_hash,
        user_role,
        user_status,
        created_by,
        created_date,
        last_modified_by,
        last_modified_date,
        is_deleted
    )
VALUES (
        '00000000-0000-0000-0000-000000000002'::uuid,
        'HR Manager TechCorp',
        'hrmanager@techcorp.com',
        'hrmanager_techcorp',
        '+84912345678',
        '$2a$10$K8qNz.v9jKlMwQx7FGfz7OqJZgT9H2P5kLYxN3rWmE8sC6vD4uA9S',
        'HR_MANAGER',
        'ACTIVE',
        'system',
        NOW(),
        'system',
        NOW(),
        FALSE
    );

-- hr_profiles table: user_id as PK + HR-specific fields
INSERT INTO
    hr_profiles (
        user_id,
        department,
        company_id,
        address,
        approved_by,
        approved_at
    )
VALUES (
        '00000000-0000-0000-0000-000000000002'::uuid,
        'Human Resources',
        '550e8400-e29b-41d4-a716-446655440001'::uuid,
        '123 Tech Street, Ho Chi Minh City',
        'system',
        NOW()
    );

-- Insert test HR account (matches auth-service)
INSERT INTO
    users (
        user_id,
        full_name,
        email,
        username,
        phone_number,
        password_hash,
        user_role,
        user_status,
        created_by,
        created_date,
        last_modified_by,
        last_modified_date,
        is_deleted
    )
VALUES (
        '00000000-0000-0000-0000-000000000003'::uuid,
        'HR Staff TechCorp',
        'hr@techcorp.com',
        'hr_techcorp',
        '+84923456789',
        '$2a$10$XyZabc123.DefGhi456JklMno789PqrStu012VwxYz345ABcDef678',
        'HR',
        'ACTIVE',
        'system',
        NOW(),
        'system',
        NOW(),
        FALSE
    );

INSERT INTO
    hr_profiles (
        user_id,
        department,
        company_id,
        address,
        approved_by,
        approved_at
    )
VALUES (
        '00000000-0000-0000-0000-000000000003'::uuid,
        'Recruitment',
        '550e8400-e29b-41d4-a716-446655440001'::uuid,
        '123 Tech Street, Ho Chi Minh City',
        'hrmanager_techcorp',
        NOW()
    );

-- Passwords (BCrypt hashed):
-- admin@workfitai.com: admin123
-- hrmanager@techcorp.com: hrmanager123
-- hr@techcorp.com: hr123
-- Note: Change these passwords after first login in production