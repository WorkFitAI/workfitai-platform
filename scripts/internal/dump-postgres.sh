#!/bin/sh
set -e

DUMP_TAG="${DUMP_TAG:-latest}"
DUMP_DIR="/dumps/${DUMP_TAG}/postgres"
mkdir -p "$DUMP_DIR"

echo "--- PostgreSQL Dump (tag: $DUMP_TAG) ---"

# Wait for user-postgres to be ready
echo "Waiting for user-postgres..."
until PGPASSWORD="${USER_DB_PASS:-pass}" pg_isready \
  -h "${USER_POSTGRES_HOST:-user-postgres}" \
  -p "${USER_POSTGRES_PORT:-5432}" \
  -U "${USER_DB_USER:-user}" > /dev/null 2>&1; do
  sleep 2
done

# Wait for job-postgres to be ready
echo "Waiting for job-postgres..."
until PGPASSWORD="${JOB_DB_PASS:-job@123}" pg_isready \
  -h "${JOB_POSTGRES_HOST:-job-postgres}" \
  -p "${JOB_POSTGRES_PORT:-5432}" \
  -U "${JOB_DB_USER:-user}" > /dev/null 2>&1; do
  sleep 2
done

echo "Dumping user_db..."
PGPASSWORD="${USER_DB_PASS:-pass}" pg_dump \
  -h "${USER_POSTGRES_HOST:-user-postgres}" \
  -p "${USER_POSTGRES_PORT:-5432}" \
  -U "${USER_DB_USER:-user}" \
  -d "${USER_DB_NAME:-user_db}" \
  -F c -f "$DUMP_DIR/user_db.dump"
echo "  user_db -> $DUMP_DIR/user_db.dump"

echo "Dumping job_db..."
PGPASSWORD="${JOB_DB_PASS:-job@123}" pg_dump \
  -h "${JOB_POSTGRES_HOST:-job-postgres}" \
  -p "${JOB_POSTGRES_PORT:-5432}" \
  -U "${JOB_DB_USER:-user}" \
  -d "${JOB_DB_NAME:-job_db}" \
  -F c -f "$DUMP_DIR/job_db.dump"
echo "  job_db -> $DUMP_DIR/job_db.dump"

echo "✅ PostgreSQL dump complete"
