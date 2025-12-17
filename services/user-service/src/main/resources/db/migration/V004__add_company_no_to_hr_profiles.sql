-- V004__add_company_no_to_hr_profiles.sql
-- Add company_no (tax ID) column to hr_profiles table
-- This is needed for dual company identification (UUID + tax ID)

-- Add company_no column (tax ID)
ALTER TABLE hr_profiles
ADD COLUMN IF NOT EXISTS company_no VARCHAR(50);

-- Create index for company_no lookups
CREATE INDEX IF NOT EXISTS idx_hr_profiles_company_no ON hr_profiles (company_no);

-- Update existing seed data with company_no
-- TechCorp Vietnam uses tax ID: 0123456789
UPDATE hr_profiles 
SET company_no = '0123456789' 
WHERE company_id = '550e8400-e29b-41d4-a716-446655440001'::uuid 
AND company_no IS NULL;

-- Add NOT NULL constraint after updating existing data
ALTER TABLE hr_profiles ALTER COLUMN company_no SET NOT NULL;

-- Add comment for documentation
COMMENT ON COLUMN hr_profiles.company_no IS 'Company tax identification number (Mã số thuế) - primary key in job-service';