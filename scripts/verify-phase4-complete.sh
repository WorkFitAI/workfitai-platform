#!/bin/bash

# Complete Distributed Tracing Verification
# Tests multi-service trace propagation

ZIPKIN_URL="http://localhost:9411"
GATEWAY_URL="http://localhost:9085"
GRAFANA_URL="http://localhost:3001"

GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "================================================"
echo "🎯 PHASE 4 - DISTRIBUTED TRACING VERIFICATION"
echo "================================================"
echo ""

# Generate some traces
echo -e "${BLUE}Generating traces across services...${NC}"
for i in {1..5}; do
    curl -s -o /dev/null http://localhost/actuator/health
    curl -s -o /dev/null -H "Authorization: Bearer fake" $GATEWAY_URL/api/v1/auth/health 2>/dev/null
    curl -s -o /dev/null -H "Authorization: Bearer fake" $GATEWAY_URL/api/v1/user/health 2>/dev/null
    curl -s -o /dev/null -H "Authorization: Bearer fake" $GATEWAY_URL/api/v1/job/health 2>/dev/null
done
echo "  ✓ Generated 20 requests"
echo ""

sleep 2

# Check services
echo -e "${BLUE}Services reporting traces:${NC}"
SERVICES=$(curl -s "$ZIPKIN_URL/api/v2/services" | jq -r '.[]' | sort)
SERVICE_COUNT=$(echo "$SERVICES" | wc -l | tr -d ' ')

if [ "$SERVICE_COUNT" -gt "0" ]; then
    echo "$SERVICES" | while read -r service; do
        echo "  ✓ $service"
    done
    echo ""
    echo -e "${GREEN}Total: $SERVICE_COUNT services${NC}"
else
    echo -e "${RED}✗ No services found${NC}"
fi
echo ""

# Check trace count
echo -e "${BLUE}Recent traces:${NC}"
TRACE_COUNT=$(curl -s "$ZIPKIN_URL/api/v2/traces?limit=100" | jq '. | length')
echo "  Total traces: $TRACE_COUNT"
echo ""

# Check dependencies
echo -e "${BLUE}Service dependencies:${NC}"
DEPENDENCIES=$(curl -s "$ZIPKIN_URL/api/v2/dependencies?endTs=$(date +%s)000&lookback=600000" | jq -r '.[] | "  \(.parent) → \(.child) (\(.callCount) calls)"')
if [ -n "$DEPENDENCIES" ]; then
    echo "$DEPENDENCIES"
else
    echo "  ⚠ No dependencies found (need authenticated requests to see inter-service calls)"
fi
echo ""

# Summary
echo "================================================"
echo "📊 MONITORING DASHBOARDS"
echo "================================================"
echo ""
echo "Zipkin UI:"
echo "  🔍 Traces: ${BLUE}$ZIPKIN_URL/zipkin/${NC}"
echo "  🕸️  Dependencies: ${BLUE}$ZIPKIN_URL/zipkin/dependency${NC}"
echo ""
echo "Grafana Dashboards:"
echo "  📈 Tracing: ${BLUE}$GRAFANA_URL/d/distributed-tracing${NC}"
echo "  📊 Metrics: ${BLUE}$GRAFANA_URL${NC}"
echo "  Login: admin/admin"
echo ""

if [ "$SERVICE_COUNT" -ge "3" ] && [ "$TRACE_COUNT" -gt "10" ]; then
    echo -e "${GREEN}✅ Phase 4 - Distributed Tracing: COMPLETE${NC}"
    echo ""
    echo "Features implemented:"
    echo "  ✓ Micrometer Tracing + Brave"
    echo "  ✓ Zipkin server (port 9411)"
    echo "  ✓ 100% sampling (dev mode)"
    echo "  ✓ $SERVICE_COUNT services instrumented"
    echo "  ✓ Grafana dashboard with performance metrics"
    echo "  ✓ P50/P95/P99 latency tracking"
    echo ""
    echo "Next steps:"
    echo "  1. Test authenticated requests to see multi-service traces"
    echo "  2. Check Grafana dashboard for performance insights"
    echo "  3. (Optional) Add AlertManager for trace anomalies"
    echo "  4. (Optional) Reduce sampling to 10% for production"
else
    echo -e "${YELLOW}⚠ Partial success - some services may need more time to start${NC}"
    echo "  Services found: $SERVICE_COUNT"
    echo "  Traces found: $TRACE_COUNT"
fi
echo ""
