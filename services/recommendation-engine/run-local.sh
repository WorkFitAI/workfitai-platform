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

# Clean FAISS index if model dimension changed
echo ""
echo -e "${YELLOW}🔍 Checking FAISS index compatibility...${NC}"
if [ -f "data/.model_dimension" ]; then
    LAST_DIMENSION=$(cat data/.model_dimension)
    CURRENT_DIMENSION=${MODEL_DIMENSION:-1024}
    
    if [ "$LAST_DIMENSION" != "$CURRENT_DIMENSION" ]; then
        echo -e "${YELLOW}⚠️  Model dimension changed: ${LAST_DIMENSION}d → ${CURRENT_DIMENSION}d${NC}"
        echo -e "${YELLOW}🗑️  Removing old FAISS index...${NC}"
        rm -f data/faiss_index*
        echo -e "${GREEN}✅ Old index removed. Will rebuild on startup.${NC}"
    else
        echo -e "${GREEN}✅ Model dimension unchanged (${CURRENT_DIMENSION}d)${NC}"
    fi
else
    # First run, clean any existing index to be safe
    echo -e "${YELLOW}First run detected, cleaning any existing FAISS index...${NC}"
    rm -f data/faiss_index*
fi

# Save current dimension for next run
echo "${MODEL_DIMENSION:-1024}" > data/.model_dimension

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
