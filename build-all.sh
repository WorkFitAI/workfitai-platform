#!/bin/bash

# ========================================
# Build WorkFitAI Microservices
# Usage:
#   ./build-all.sh                  -> build all services
#   ./build-all.sh user-service     -> build single service
#   ./build-all.sh user-service auth-service -> build multiple services
# ========================================

echo "========================================"
echo "Building WorkFitAI Services"
echo "========================================"
echo ""

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Track build status
FAILED_SERVICES=()
SUCCESS_SERVICES=()

# All available services
ALL_SERVICES=(
    "api-gateway"
    "auth-service"
    "user-service"
    "job-service"
    "application-service"
    "cv-service"
    "notification-service"
    "monitoring-service"
)

# ==============================
# Resolve services to build
# ==============================
if [ "$#" -eq 0 ]; then
    SERVICES=("${ALL_SERVICES[@]}")
    echo -e "${YELLOW}No service specified → build ALL services${NC}"
else
    SERVICES=("$@")
    echo -e "${YELLOW}Building specified services:${NC} ${SERVICES[*]}"
fi

echo ""

# ==============================
# Build function
# ==============================
build_service() {
    local service_name=$1
    local service_path="services/$service_name"

    # Validate service name
    if [[ ! " ${ALL_SERVICES[*]} " =~ " ${service_name} " ]]; then
        echo -e "${RED}[ERROR]${NC} Unknown service: $service_name"
        FAILED_SERVICES+=("$service_name (unknown)")
        return 1
    fi

    if [ ! -d "$service_path" ]; then
        echo -e "${RED}[ERROR]${NC} Service directory not found: $service_path"
        FAILED_SERVICES+=("$service_name (not found)")
        return 1
    fi

    echo -e "${YELLOW}[BUILDING]${NC} $service_name"
    cd "$service_path" || return 1

    if mvn clean install -DskipTests; then
        echo -e "${GREEN}[SUCCESS]${NC} $service_name built successfully"
        SUCCESS_SERVICES+=("$service_name")
    else
        echo -e "${RED}[FAILED]${NC} $service_name build failed"
        FAILED_SERVICES+=("$service_name")
    fi

    cd - > /dev/null || return 1
    return 0
}

# ==============================
# Build loop
# ==============================
for service in "${SERVICES[@]}"; do
    build_service "$service"
    echo ""
done

# ==============================
# Summary
# ==============================
echo "========================================"
echo "Build Summary"
echo "========================================"
echo ""

if [ ${#SUCCESS_SERVICES[@]} -gt 0 ]; then
    echo -e "${GREEN}✓ Successfully built (${#SUCCESS_SERVICES[@]}):${NC}"
    for service in "${SUCCESS_SERVICES[@]}"; do
        echo "  - $service"
    done
    echo ""
fi

if [ ${#FAILED_SERVICES[@]} -gt 0 ]; then
    echo -e "${RED}✗ Failed to build (${#FAILED_SERVICES[@]}):${NC}"
    for service in "${FAILED_SERVICES[@]}"; do
        echo "  - $service"
    done
    echo ""
    exit 1
fi

echo -e "${GREEN}All requested services built successfully!${NC}"
echo ""
echo "Next steps:"
echo "  - docker-compose build"
echo "  - docker-compose up -d"
exit 0
