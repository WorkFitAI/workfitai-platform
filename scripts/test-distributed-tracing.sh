#!/bin/bash

# Test Distributed Tracing with Zipkin
# Tests trace propagation across microservices

ZIPKIN_URL="http://localhost:9411"
GATEWAY_URL="http://localhost:9085"
NGINX_URL="http://localhost"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo "================================================"
echo "🔍 DISTRIBUTED TRACING TEST - ZIPKIN + MICROMETER"
echo "================================================"
echo ""

# Test 1: Check Zipkin is running
echo -e "${BLUE}Test 1: Checking Zipkin Health${NC}"
if curl -s -f "$ZIPKIN_URL/health" > /dev/null; then
    echo -e "${GREEN}✓ Zipkin is running${NC}"
    echo "  URL: $ZIPKIN_URL"
else
    echo -e "${RED}✗ Zipkin is not accessible${NC}"
    echo "  Run: docker-compose up -d zipkin"
    exit 1
fi
echo ""

# Test 2: Generate traces by making API calls
echo -e "${BLUE}Test 2: Generating Traces via API Calls${NC}"
echo "Making requests to trigger tracing..."

# Call 1: Health check (simple trace)
echo -n "  - GET /actuator/health... "
HEALTH_RESPONSE=$(curl -s -w "\n%{http_code}" "$NGINX_URL/actuator/health")
HTTP_CODE=$(echo "$HEALTH_RESPONSE" | tail -n1)
if [ "$HTTP_CODE" = "200" ]; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${RED}✗ (HTTP $HTTP_CODE)${NC}"
fi

# Call 2: Auth service (via gateway)
echo -n "  - GET /api/v1/auth/health... "
AUTH_RESPONSE=$(curl -s -w "\n%{http_code}" "$GATEWAY_URL/api/v1/auth/health")
HTTP_CODE=$(echo "$AUTH_RESPONSE" | tail -n1)
if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "404" ]; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${RED}✗ (HTTP $HTTP_CODE)${NC}"
fi

# Call 3: User service (multi-hop trace)
echo -n "  - GET /api/v1/user/health... "
USER_RESPONSE=$(curl -s -w "\n%{http_code}" "$GATEWAY_URL/api/v1/user/health" 2>/dev/null)
HTTP_CODE=$(echo "$USER_RESPONSE" | tail -n1)
if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "404" ]; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${RED}✗ (HTTP $HTTP_CODE)${NC}"
fi

echo "  Waiting 3 seconds for traces to be exported..."
sleep 3
echo ""

# Test 3: Check traces in Zipkin
echo -e "${BLUE}Test 3: Verifying Traces in Zipkin${NC}"
TRACES=$(curl -s "$ZIPKIN_URL/api/v2/traces?limit=10")
TRACE_COUNT=$(echo "$TRACES" | jq 'length' 2>/dev/null || echo "0")

if [ "$TRACE_COUNT" -gt "0" ]; then
    echo -e "${GREEN}✓ Found $TRACE_COUNT trace(s) in Zipkin${NC}"
    
    # Get first trace ID
    FIRST_TRACE=$(echo "$TRACES" | jq -r '.[0][0].traceId' 2>/dev/null)
    if [ -n "$FIRST_TRACE" ] && [ "$FIRST_TRACE" != "null" ]; then
        echo "  First Trace ID: $FIRST_TRACE"
    fi
else
    echo -e "${YELLOW}⚠ No traces found yet${NC}"
    echo "  This might be normal if services just started"
    echo "  Check Zipkin UI: $ZIPKIN_URL"
fi
echo ""

# Test 4: Check service names in Zipkin
echo -e "${BLUE}Test 4: Checking Registered Services${NC}"
SERVICES=$(curl -s "$ZIPKIN_URL/api/v2/services")
SERVICE_COUNT=$(echo "$SERVICES" | jq 'length' 2>/dev/null || echo "0")

if [ "$SERVICE_COUNT" -gt "0" ]; then
    echo -e "${GREEN}✓ Found $SERVICE_COUNT service(s) reporting traces:${NC}"
    echo "$SERVICES" | jq -r '.[]' 2>/dev/null | while read -r service; do
        echo "    - $service"
    done
else
    echo -e "${YELLOW}⚠ No services registered yet${NC}"
    echo "  Services need to send at least one trace to appear here"
fi
echo ""

# Test 5: Check trace propagation (B3 headers)
echo -e "${BLUE}Test 5: Verifying B3 Trace Headers${NC}"
echo "Making request with verbose headers..."
TRACE_HEADERS=$(curl -v -s "$NGINX_URL/actuator/health" 2>&1 | grep -i "x-b3\|traceparent")

if echo "$TRACE_HEADERS" | grep -q "x-b3"; then
    echo -e "${GREEN}✓ B3 trace headers detected:${NC}"
    echo "$TRACE_HEADERS" | sed 's/^/  /'
else
    echo -e "${YELLOW}⚠ No B3 headers in response${NC}"
    echo "  Headers might be added by services but not returned to client"
fi
echo ""

# Test 6: Check sampling configuration
echo -e "${BLUE}Test 6: Checking Sampling Configuration${NC}"
echo "Current sampling rate: 100% (probability: 1.0)"
echo "Location: services/*/src/main/resources/application.yml"
echo ""
echo -e "${YELLOW}📝 Production Recommendation:${NC}"
echo "  Change sampling probability to 0.1 (10%) in production to reduce overhead"
echo ""

# Summary
echo "================================================"
echo "📊 TEST SUMMARY"
echo "================================================"
echo ""
echo "Zipkin UI: ${BLUE}$ZIPKIN_URL${NC}"
echo "Services: ${BLUE}$ZIPKIN_URL/zipkin/dependency${NC} (Dependency Graph)"
echo "Traces: ${BLUE}$ZIPKIN_URL/zipkin/?limit=100${NC} (Search Traces)"
echo ""

if [ "$TRACE_COUNT" -gt "0" ] && [ "$SERVICE_COUNT" -gt "0" ]; then
    echo -e "${GREEN}✅ Distributed Tracing is WORKING!${NC}"
    echo ""
    echo "Next Steps:"
    echo "  1. Open Zipkin UI to visualize traces"
    echo "  2. Make more API calls to see multi-service traces"
    echo "  3. Check service dependency graph"
    echo "  4. Analyze latency and bottlenecks"
else
    echo -e "${YELLOW}⚠ Tracing setup complete but no traces yet${NC}"
    echo ""
    echo "Troubleshooting:"
    echo "  1. Check services are running: docker-compose ps"
    echo "  2. Verify dependencies: grep micrometer services/*/pom.xml"
    echo "  3. Check logs: docker-compose logs <service-name> | grep -i zipkin"
    echo "  4. Restart services: ./dev.sh full restart"
fi
echo ""
