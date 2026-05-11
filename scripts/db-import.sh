#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

DUMP_TAG="${1:-${DUMP_TAG:-latest}}"
export DUMP_TAG

if [ ! -d "$PROJECT_DIR/dumps/$DUMP_TAG" ]; then
  echo "❌ Dump not found: ./dumps/$DUMP_TAG"
  echo ""
  echo "Available dumps:"
  ls -1 "$PROJECT_DIR/dumps/" 2>/dev/null | grep -v '^$' || echo "  (none)"
  exit 1
fi

echo "📥 Importing all databases (tag: $DUMP_TAG)..."
echo "   Source: $PROJECT_DIR/dumps/$DUMP_TAG/"

cd "$PROJECT_DIR"

DUMP_TAG=$DUMP_TAG docker-compose --profile db-import up --abort-on-container-exit
docker-compose --profile db-import down --remove-orphans 2>/dev/null || true

echo ""
echo "✅ Import complete from ./dumps/$DUMP_TAG/"
