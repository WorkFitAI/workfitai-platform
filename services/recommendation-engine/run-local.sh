#!/bin/bash

# Run Recommendation Engine Locally
# This script runs the recommendation engine on your Mac
# while connecting to services (Vault, Kafka) running in Docker

set -e

echo "🚀 Starting Recommendation Engine (Local Mode)"
echo "================================================"

# Color codes
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Check if .env.local exists
if [ ! -f .env.local ]; then
    echo -e "${RED}❌ Error: .env.local not found${NC}"
    echo "Please create .env.local file first"
    exit 1
fi

# Check if virtual environment exists
if [ ! -d "venv" ]; then
    echo -e "${YELLOW}⚠️  Virtual environment not found. Creating...${NC}"
    python3 -m venv venv
fi

# Activate virtual environment
echo -e "${GREEN}✅ Activating virtual environment${NC}"
source venv/bin/activate

# Install/Update dependencies
if [ ! -f "venv/.deps_installed" ] || [ requirements.txt -nt venv/.deps_installed ]; then
    echo -e "${YELLOW}📦 Installing dependencies...${NC}"
    pip install --upgrade pip
    pip install -r requirements.txt
    touch venv/.deps_installed
else
    echo -e "${GREEN}✅ Dependencies already installed${NC}"
fi

# Check if Docker services are running
echo ""
echo -e "${YELLOW}🔍 Checking Docker services...${NC}"

check_service() {
    local service=$1
    local port=$2
    if nc -z localhost $port 2>/dev/null; then
        echo -e "${GREEN}✅ $service is running on port $port${NC}"
        return 0
    else
        echo -e "${RED}❌ $service is NOT running on port $port${NC}"
        return 1
    fi
}

MISSING_SERVICES=0

check_service "Vault" 8200 || MISSING_SERVICES=$((MISSING_SERVICES + 1))
check_service "Kafka" 9092 || MISSING_SERVICES=$((MISSING_SERVICES + 1))
check_service "Consul" 8500 || ((MISSING_SERVICES++)) || true  # Optional

if [ $MISSING_SERVICES -gt 0 ]; then
    echo ""
    echo -e "${RED}❌ Some required services are not running${NC}"
    echo -e "${YELLOW}Please start infrastructure services first:${NC}"
    echo "  cd ../../ && ./dev.sh infra up"
    echo ""
    read -p "Do you want to continue anyway? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

# Load environment variables
echo ""
echo -e "${GREEN}✅ Loading environment variables from .env.local${NC}"
export $(grep -v '^#' .env.local | xargs)

# Create necessary directories
mkdir -p data logs models

# Check if model exists
if [ ! -d "models/bi-encoder-e5-large" ]; then
    echo -e "${YELLOW}⚠️  Model not found in models/bi-encoder-e5-large${NC}"
    echo "The model will be downloaded on first run (may take time)"
fi

# Print configuration
echo ""
echo -e "${GREEN}📋 Configuration:${NC}"
echo "  Environment: ${ENVIRONMENT:-local}"
echo "  Port: ${PORT:-8000}"
echo "  Vault: ${VAULT_ADDR}"
echo "  Kafka: ${KAFKA_BOOTSTRAP_SERVERS}"
echo "  Model Path: ${MODEL_PATH}"
echo "  FAISS Index: ${FAISS_INDEX_PATH}"
echo ""

# Run the application
echo -e "${GREEN}🚀 Starting server...${NC}"
echo -e "${YELLOW}Press Ctrl+C to stop${NC}"
echo ""

# Run with uvicorn (convert LOG_LEVEL to lowercase for uvicorn)
LOG_LEVEL_LOWER=$(echo "${LOG_LEVEL:-info}" | tr '[:upper:]' '[:lower:]')
python -m uvicorn app.main:app \
    --host ${HOST:-0.0.0.0} \
    --port ${PORT:-8000} \
    --reload \
    --log-level ${LOG_LEVEL_LOWER}
export KAFKA_CONSUMER_GROUP=recommendation-engine
export KAFKA_TOPIC_JOB_CREATED=job.created
export KAFKA_TOPIC_JOB_UPDATED=job.updated
export KAFKA_TOPIC_JOB_DELETED=job.deleted

# Job Service configuration (connect via API Gateway)
export JOB_SERVICE_URL=http://localhost:9085
export JOB_SERVICE_TIMEOUT=30

# Model configuration
export MODEL_PATH=sentence-transformers/all-MiniLM-L6-v2
export MODEL_DIMENSION=384  # MiniLM dimension
export BATCH_SIZE=32

# FAISS configuration (local data directory)
export FAISS_INDEX_PATH=./data/faiss_index
export ENABLE_INDEX_PERSISTENCE=true

# Create data directory
mkdir -p data logs

# Download model first (to avoid blocking startup)
echo "📥 Pre-downloading Sentence Transformer model..."
python3 -c "
from sentence_transformers import SentenceTransformer
import os
model_path = os.getenv('MODEL_PATH', 'sentence-transformers/all-MiniLM-L6-v2')
print(f'Downloading {model_path}...')
model = SentenceTransformer(model_path)
print(f'✓ Model downloaded: {model_path}')
print(f'  Dimension: {model.get_sentence_embedding_dimension()}')
print(f'  Max sequence length: {model.max_seq_length}')
"

echo ""
echo "================================================"
echo "✅ Ready to start!"
echo "================================================"
echo ""
echo "Starting Uvicorn server..."
echo "API will be available at: http://localhost:8001"
echo "Health check: http://localhost:8001/health"
echo ""
echo "Press Ctrl+C to stop"
echo ""

# Run FastAPI with Uvicorn
uvicorn app.main:app \
    --host $HOST \
    --port $PORT \
    --reload \
    --log-level info
