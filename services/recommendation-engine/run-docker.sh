#!/bin/bash
set -e

DATA_DIR=/app/data

echo "Starting Recommendation Engine (Docker Mode)"
echo "============================================="

# Create necessary directories
mkdir -p "$DATA_DIR" /app/logs /app/models

# Clean FAISS index on every start to force resync from job-service
echo "Cleaning FAISS index for fresh sync..."
find "$DATA_DIR" -type f \( -name 'faiss_index*' -o -name '*.pkl' -o -name '*.json' \) -delete 2>/dev/null || true

# Clean FAISS index if model dimension changed
if [ -f "$DATA_DIR/.model_dimension" ]; then
    LAST_DIMENSION=$(cat "$DATA_DIR/.model_dimension")
    CURRENT_DIMENSION=${MODEL_DIMENSION:-1024}

    if [ "$LAST_DIMENSION" != "$CURRENT_DIMENSION" ]; then
        echo "Model dimension changed: ${LAST_DIMENSION}d -> ${CURRENT_DIMENSION}d, removing old index..."
        rm -f "$DATA_DIR"/faiss_index*
    else
        echo "Model dimension unchanged (${CURRENT_DIMENSION}d)"
    fi
else
    echo "First run: cleaned any existing FAISS index"
    rm -f "$DATA_DIR"/faiss_index*
fi

echo "${MODEL_DIMENSION:-1024}" > "$DATA_DIR/.model_dimension"

echo ""
echo "Configuration:"
echo "  Environment: ${ENVIRONMENT:-docker}"
echo "  Port:        ${PORT:-8000}"
echo "  Vault:       ${VAULT_ADDR}"
echo "  Kafka:       ${KAFKA_BOOTSTRAP_SERVERS}"
echo "  Model Path:  ${MODEL_PATH}"
echo "  FAISS Index: ${FAISS_INDEX_PATH}"
echo ""

LOG_LEVEL_LOWER=$(echo "${LOG_LEVEL:-info}" | tr '[:upper:]' '[:lower:]')
exec python -m uvicorn app.main:app \
    --host "${HOST:-0.0.0.0}" \
    --port "${PORT:-8000}" \
    --workers 1 \
    --log-level "$LOG_LEVEL_LOWER" \
    --timeout-keep-alive 300
