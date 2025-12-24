#!/bin/bash

# Script to generate traffic for testing Grafana dashboards
# This will create realistic traffic patterns to populate metrics

GATEWAY_URL="http://localhost:9085"
COLORS='\033[0;32m' # Green
NC='\033[0m' # No Color

echo -e "${COLORS}=== Generating API Gateway Traffic ===${NC}"
echo "This will make various requests to populate Grafana dashboards"
echo ""

# Function to make request and show result
make_request() {
    local method=$1
    local endpoint=$2
    local description=$3
    local content_type=$4
    local auth_header=$5
    
    echo -n "[$method] $endpoint - $description ... "
    
    if [ "$method" = "GET" ]; then
        response=$(curl -s -w "\n%{http_code}" -X GET "$GATEWAY_URL$endpoint" \
            ${content_type:+-H "Content-Type: $content_type"} \
            ${auth_header:+-H "Authorization: Bearer $auth_header"} \
            2>/dev/null)
    else
        response=$(curl -s -w "\n%{http_code}" -X POST "$GATEWAY_URL$endpoint" \
            ${content_type:+-H "Content-Type: $content_type"} \
            ${auth_header:+-H "Authorization: Bearer $auth_header"} \
            -d '{"test": "data"}' \
            2>/dev/null)
    fi
    
    status_code=$(echo "$response" | tail -n1)
    
    if [ "$status_code" -ge 200 ] && [ "$status_code" -lt 300 ]; then
        echo -e "${COLORS}✓ $status_code${NC}"
    elif [ "$status_code" -ge 400 ] && [ "$status_code" -lt 500 ]; then
        echo "⚠ $status_code (expected for validation tests)"
    else
        echo "✗ $status_code"
    fi
}

# 1. Health Check Requests (will be cached)
echo -e "\n${COLORS}1. Health Check Requests (Cache Test)${NC}"
for i in {1..10}; do
    make_request "GET" "/actuator/health" "Health check #$i"
    sleep 0.5
done

# 2. Rate Limit Testing (Phase 1)
echo -e "\n${COLORS}2. Rate Limit Testing${NC}"
for i in {1..20}; do
    make_request "GET" "/actuator/health" "Rate limit test #$i"
done

# 3. Request Validation Testing (Phase 3)
echo -e "\n${COLORS}3. Request Validation - Missing Content-Type${NC}"
for i in {1..5}; do
    curl -s -X POST "$GATEWAY_URL/api/v1/auth/register" -d '{"test": "data"}' -o /dev/null -w "Status: %{http_code}\n"
done

echo -e "\n${COLORS}4. Request Validation - Invalid Content-Type${NC}"
for i in {1..5}; do
    curl -s -X POST "$GATEWAY_URL/api/v1/auth/register" \
        -H "Content-Type: text/plain" \
        -d '{"test": "data"}' \
        -o /dev/null -w "Status: %{http_code}\n"
done

echo -e "\n${COLORS}5. Request Validation - Valid Content-Type${NC}"
for i in {1..5}; do
    curl -s -X POST "$GATEWAY_URL/api/v1/auth/register" \
        -H "Content-Type: application/json" \
        -d '{"email":"test@example.com","password":"Test123!","role":"CANDIDATE"}' \
        -o /dev/null -w "Status: %{http_code}\n"
done

# 6. Various endpoints for routing metrics
echo -e "\n${COLORS}6. Various Endpoints (Routing Metrics)${NC}"
make_request "GET" "/api/v1/auth/health" "Auth service health"
make_request "GET" "/api/v1/users/health" "User service health"
make_request "GET" "/api/v1/jobs/health" "Job service health"
make_request "GET" "/api/v1/cv/health" "CV service health"
make_request "GET" "/api/v1/applications/health" "Application service health"

# 7. Prometheus metrics endpoint
echo -e "\n${COLORS}7. Metrics Endpoint${NC}"
make_request "GET" "/actuator/prometheus" "Fetch metrics"

# 8. Continuous load (background)
echo -e "\n${COLORS}8. Generating Continuous Load (30 seconds)${NC}"
echo "This will create steady traffic for time-series data..."

for i in {1..30}; do
    curl -s "$GATEWAY_URL/actuator/health" > /dev/null &
    curl -s "$GATEWAY_URL/api/v1/auth/health" > /dev/null &
    
    if [ $((i % 5)) -eq 0 ]; then
        echo -n "."
    fi
    sleep 1
done

wait
echo ""

echo -e "\n${COLORS}=== Traffic Generation Complete ===${NC}"
echo ""
echo "Next steps:"
echo "1. Open Grafana: http://localhost:3001"
echo "2. Navigate to API Gateway Overview dashboard"
echo "3. Check that panels now show data"
echo "4. If still 'No data', check:"
echo "   - Prometheus targets: http://localhost:9090/targets"
echo "   - API Gateway metrics: http://localhost:9085/actuator/prometheus"
echo "   - Time range in Grafana (try 'Last 5 minutes')"
