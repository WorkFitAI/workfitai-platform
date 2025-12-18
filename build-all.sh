#!/bin/bash

# Build all services script
# This script builds all microservices using Maven

echo "========================================"
echo "Building All WorkFitAI Services"
echo "========================================"
echo ""

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Track build status
FAILED_SERVICES=()
SUCCESS_SERVICES=()

# Function to build a service
build_service() {
    local service_name=$1
    local service_path="services/$service_name"
    
    if [ ! -d "$service_path" ]; then
        echo -e "${RED}[ERROR]${NC} Service directory not found: $service_path"
        FAILED_SERVICES+=("$service_name (not found)")
        return 1
    fi
    
    echo -e "${YELLOW}[BUILDING]${NC} $service_name..."
    cd "$service_path" || return 1
    
    if mvn clean install -DskipTests; then
        echo -e "${GREEN}[SUCCESS]${NC} $service_name built successfully"
        SUCCESS_SERVICES+=("$service_name")
        cd ../.. || return 1
        return 0
    else
        echo -e "${RED}[FAILED]${NC} $service_name build failed"
        FAILED_SERVICES+=("$service_name")
        cd ../.. || return 1
        return 1
    fi
}

# List of services to build
SERVICES=(
    "api-gateway"
    "auth-service"
    "user-service"
    "job-service"
    "application-service"
    "cv-service"
    "notification-service"
    "monitoring-service"
)

echo "Services to build: ${SERVICES[*]}"
echo ""

# Build each service
for service in "${SERVICES[@]}"; do
    build_service "$service"
    echo ""
done

# Print summary
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

echo -e "${GREEN}All services built successfully!${NC}"
echo ""
echo "You can now run: docker-compose build"
exit 0
