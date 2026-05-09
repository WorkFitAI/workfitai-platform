#!/bin/sh
set -e

DUMP_TAG="${DUMP_TAG:-latest}"
DUMP_DIR="/dumps/${DUMP_TAG}/mongo"

if [ ! -d "$DUMP_DIR" ]; then
  echo "❌ Dump dir not found: $DUMP_DIR"
  exit 1
fi

echo "--- MongoDB Import (tag: $DUMP_TAG) ---"

wait_for_mongo() {
  local host="$1"
  local port="${2:-27017}"
  echo "Waiting for $host:$port..."
  until mongosh --host "$host" --port "$port" --eval "db.adminCommand('ping')" --quiet > /dev/null 2>&1; do
    sleep 2
  done
}

wait_for_mongo "${AUTH_MONGO_HOST:-auth-mongo}" 27017
wait_for_mongo "${CV_MONGO_HOST:-cv-mongo}" 27017
wait_for_mongo "${APP_MONGO_HOST:-application-mongo}" 27017
wait_for_mongo "${NOTIF_MONGO_HOST:-notif-mongo}" 27017

if [ -d "$DUMP_DIR/auth-db" ]; then
  echo "Importing auth-db..."
  mongorestore \
    --host "${AUTH_MONGO_HOST:-auth-mongo}:27017" \
    --db auth-db \
    --drop \
    "$DUMP_DIR/auth-db"
  echo "  ✅ auth-db imported"
else
  echo "  ⚠️  auth-db dump not found, skipping"
fi

if [ -d "$DUMP_DIR/${CV_DB_NAME:-cv-db}" ]; then
  echo "Importing cv-db..."
  mongorestore \
    --host "${CV_MONGO_HOST:-cv-mongo}:27017" \
    --username "${CV_DB_USER:-user}" \
    --password "${CV_DB_PASS:-123456}" \
    --authenticationDatabase "${CV_DB_NAME:-cv-db}" \
    --db "${CV_DB_NAME:-cv-db}" \
    --drop \
    "$DUMP_DIR/${CV_DB_NAME:-cv-db}"
  echo "  ✅ cv-db imported"
else
  echo "  ⚠️  cv-db dump not found, skipping"
fi

if [ -d "$DUMP_DIR/app-db" ]; then
  echo "Importing app-db..."
  mongorestore \
    --host "${APP_MONGO_HOST:-application-mongo}:27017" \
    --db app-db \
    --drop \
    "$DUMP_DIR/app-db"
  echo "  ✅ app-db imported"
else
  echo "  ⚠️  app-db dump not found, skipping"
fi

if [ -d "$DUMP_DIR/notification-db" ]; then
  echo "Importing notification-db..."
  mongorestore \
    --host "${NOTIF_MONGO_HOST:-notif-mongo}:27017" \
    --db notification-db \
    --drop \
    "$DUMP_DIR/notification-db"
  echo "  ✅ notification-db imported"
else
  echo "  ⚠️  notification-db dump not found, skipping"
fi

echo "✅ MongoDB import complete"
