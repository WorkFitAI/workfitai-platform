#!/bin/sh
set -e

DUMP_TAG="${DUMP_TAG:-latest}"
DUMP_DIR="/dumps/${DUMP_TAG}/postgres"

if [ ! -d "$DUMP_DIR" ]; then
  echo "❌ Dump dir not found: $DUMP_DIR"
  exit 1
fi

echo "--- PostgreSQL Import (tag: $DUMP_TAG) ---"

wait_for_postgres() {
  local host="$1" port="$2" user="$3" pass="$4"
  echo "Waiting for $host:$port..."
  until PGPASSWORD="$pass" pg_isready -h "$host" -p "$port" -U "$user" > /dev/null 2>&1; do
    sleep 2
  done
}

wait_for_postgres \
  "${USER_POSTGRES_HOST:-user-postgres}" \
  "${USER_POSTGRES_PORT:-5432}" \
  "${USER_DB_USER:-user}" \
  "${USER_DB_PASS:-pass}"

wait_for_postgres \
  "${JOB_POSTGRES_HOST:-job-postgres}" \
  "${JOB_POSTGRES_PORT:-5432}" \
  "${JOB_DB_USER:-user}" \
  "${JOB_DB_PASS:-job@123}"

if [ -f "$DUMP_DIR/user_db.dump" ]; then
  echo "Importing user_db..."
  PGPASSWORD="${USER_DB_PASS:-pass}" pg_restore \
    -h "${USER_POSTGRES_HOST:-user-postgres}" \
    -p "${USER_POSTGRES_PORT:-5432}" \
    -U "${USER_DB_USER:-user}" \
    -d "${USER_DB_NAME:-user_db}" \
    --clean --if-exists --no-owner \
    "$DUMP_DIR/user_db.dump"
  echo "  ✅ user_db imported"
else
  echo "  ⚠️  user_db.dump not found, skipping"
fi

if [ -f "$DUMP_DIR/job_db.dump" ]; then
  echo "Importing job_db..."
  PGPASSWORD="${JOB_DB_PASS:-job@123}" pg_restore \
    -h "${JOB_POSTGRES_HOST:-job-postgres}" \
    -p "${JOB_POSTGRES_PORT:-5432}" \
    -U "${JOB_DB_USER:-user}" \
    -d "${JOB_DB_NAME:-job_db}" \
    --clean --if-exists --no-owner \
    "$DUMP_DIR/job_db.dump"
  echo "  ✅ job_db imported"
else
  echo "  ⚠️  job_db.dump not found, skipping"
fi

echo "✅ PostgreSQL import complete"
