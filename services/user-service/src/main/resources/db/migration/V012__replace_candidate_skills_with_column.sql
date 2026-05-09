-- V012__replace_candidate_skills_with_column.sql
-- Drop the candidate_profile_skills table and replace with a skills TEXT column on candidates

DROP TABLE IF EXISTS candidate_profile_skills;

ALTER TABLE candidates ADD COLUMN IF NOT EXISTS skills TEXT;
