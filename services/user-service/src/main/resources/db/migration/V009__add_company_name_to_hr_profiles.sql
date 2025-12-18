-- V009__add_company_name_to_hr_profiles.sql
-- Add company_name column to hr_profiles table
-- Store company name from registration to send to job-service

-- Add company_name column
ALTER TABLE hr_profiles ADD COLUMN IF NOT EXISTS company_name VARCHAR(255);

-- Add comment for documentation
COMMENT ON COLUMN hr_profiles.company_name IS 'Company name from registration - used for job-service synchronization';
