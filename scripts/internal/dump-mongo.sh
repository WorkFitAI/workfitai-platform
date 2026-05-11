#!/bin/sh
set -e

DUMP_TAG="${DUMP_TAG:-latest}"
DUMP_DIR="/dumps/${DUMP_TAG}/mongo"
mkdir -p "$DUMP_DIR"

echo "--- MongoDB Dump (tag: $DUMP_TAG) ---"

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

echo "Dumping auth-db..."
mongodump \
  --host "${AUTH_MONGO_HOST:-auth-mongo}:27017" \
  --db auth-db \
  --out "$DUMP_DIR"
echo "  auth-db -> $DUMP_DIR/auth-db/"

echo "Dumping cv-db..."
mongodump \
  --host "${CV_MONGO_HOST:-cv-mongo}:27017" \
  --username "${CV_DB_USER:-user}" \
  --password "${CV_DB_PASS:-123456}" \
  --authenticationDatabase "${CV_DB_NAME:-cv-db}" \
  --db "${CV_DB_NAME:-cv-db}" \
  --out "$DUMP_DIR"
echo "  cv-db -> $DUMP_DIR/cv-db/"

echo "Dumping app-db..."
mongodump \
  --host "${APP_MONGO_HOST:-application-mongo}:27017" \
  --db app-db \
  --out "$DUMP_DIR"
echo "  app-db -> $DUMP_DIR/app-db/"

echo "Dumping notification-db..."
mongodump \
  --host "${NOTIF_MONGO_HOST:-notif-mongo}:27017" \
  --db notification-db \
  --out "$DUMP_DIR"
echo "  notification-db -> $DUMP_DIR/notification-db/"

echo "✅ MongoDB dump complete"
