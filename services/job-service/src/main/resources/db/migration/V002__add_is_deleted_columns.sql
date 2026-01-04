-- V002__add_is_deleted_columns.sql

-- Jobs
ALTER TABLE jobs
    ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_jobs_is_deleted ON jobs (is_deleted);
