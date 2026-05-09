#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

DUMP_TAG="${1:-${DUMP_TAG:-latest}}"
export DUMP_TAG

echo "🗄️  Dumping all databases (tag: $DUMP_TAG)..."
echo "   Output: $PROJECT_DIR/dumps/$DUMP_TAG/"

cd "$PROJECT_DIR"

DUMP_TAG=$DUMP_TAG docker-compose --profile db-dump up --abort-on-container-exit
docker-compose --profile db-dump down --remove-orphans 2>/dev/null || true

echo ""
echo "✅ All dumps saved to ./dumps/$DUMP_TAG/"
echo "   PostgreSQL: ./dumps/$DUMP_TAG/postgres/"
echo "   MongoDB:    ./dumps/$DUMP_TAG/mongo/"
