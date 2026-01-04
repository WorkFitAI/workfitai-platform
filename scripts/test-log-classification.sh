#!/bin/bash

# Quick Test Script for Log Classification System
# Tests with currently running services

echo "🧪 Testing Log Classification System"
echo "===================================="
echo ""

MONITORING_URL="http://localhost:9086"
ES_URL="http://localhost:9200"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test 1: Check if services are running
echo "📌 Test 1: Check service health"
services=("auth-service:9080" "user-service:9081" "monitoring-service:9086")

for service_port in "${services[@]}"; do
    service="${service_port%%:*}"
    port="${service_port##*:}"
    
    if curl -s "http://localhost:${port}/actuator/health" > /dev/null 2>&1; then
        echo -e "${GREEN}✓${NC} ${service} is running on port ${port}"
    else
        echo -e "${RED}✗${NC} ${service} is NOT running on port ${port}"
    fi
done
echo ""

# Test 2: Make a test user action
echo "📌 Test 2: Generate user activity log"
echo "Making test request to auth-service..."

# Simple health check should create a HEALTH_CHECK log
curl -s "http://localhost:9080/actuator/health" > /dev/null
echo -e "${GREEN}✓${NC} Test request sent"
echo ""

# Wait for logs to be processed
echo "⏳ Waiting 5 seconds for log processing..."
sleep 5
echo ""

# Test 3: Query Elasticsearch for log_type field
echo "📌 Test 3: Check if log_type field exists in Elasticsearch"
if curl -s "${ES_URL}/workfitai-logs-*/_search?size=1&q=log_type:*&pretty" | grep -q "log_type"; then
    echo -e "${GREEN}✓${NC} log_type field exists in Elasticsearch"
else
    echo -e "${RED}✗${NC} log_type field NOT found in Elasticsearch"
fi
echo ""

# Test 4: Count logs by type
echo "📌 Test 4: Count logs by type (last 1 hour)"
echo "Query: ${ES_URL}/workfitai-logs-*/_search"

RESULT=$(curl -s -X POST "${ES_URL}/workfitai-logs-*/_search" \
  -H 'Content-Type: application/json' \
  -d '{
  "size": 0,
  "query": {
    "range": {
      "@timestamp": {
        "gte": "now-1h"
      }
    }
  },
  "aggs": {
    "by_log_type": {
      "terms": {
        "field": "log_type.keyword",
        "size": 10
      }
    }
  }
}' 2>/dev/null)

if echo "$RESULT" | grep -q "by_log_type"; then
    echo -e "${GREEN}✓${NC} Log type aggregation works"
    echo "$RESULT" | jq '.aggregations.by_log_type.buckets[] | {type: .key, count: .doc_count}' 2>/dev/null || echo "   (Install jq for prettier output)"
else
    echo -e "${YELLOW}⚠${NC}  No log type data found (services may need more time)"
fi
echo ""

# Test 5: Test user activity endpoint
echo "📌 Test 5: Test /api/logs/activity endpoint"
echo "Query: ${MONITORING_URL}/api/logs/activity?hours=1"

if curl -s "${MONITORING_URL}/api/logs/activity?hours=1" > /dev/null 2>&1; then
    ACTIVITY=$(curl -s "${MONITORING_URL}/api/logs/activity?hours=1")
    TOTAL=$(echo "$ACTIVITY" | jq -r '.total' 2>/dev/null || echo "0")
    
    if [ "$TOTAL" != "null" ] && [ "$TOTAL" != "0" ]; then
        echo -e "${GREEN}✓${NC} Activity endpoint works - Found ${TOTAL} user actions"
        echo "$ACTIVITY" | jq '.activities[0:3] | .[] | {username, logType, action, path}' 2>/dev/null || echo "   (Install jq to see details)"
    else
        echo -e "${YELLOW}⚠${NC}  No user activities found yet (expected if no authenticated requests)"
    fi
else
    echo -e "${RED}✗${NC} Activity endpoint not accessible"
fi
echo ""

# Test 6: Verify filter works
echo "📌 Test 6: Verify USER_ACTION filtering"
USER_ACTIONS=$(curl -s "${ES_URL}/workfitai-logs-*/_count?q=log_type:USER_ACTION" 2>/dev/null | jq -r '.count' 2>/dev/null || echo "0")
HEALTH_CHECKS=$(curl -s "${ES_URL}/workfitai-logs-*/_count?q=log_type:HEALTH_CHECK" 2>/dev/null | jq -r '.count' 2>/dev/null || echo "0")
SYSTEM_LOGS=$(curl -s "${ES_URL}/workfitai-logs-*/_count?q=log_type:SYSTEM" 2>/dev/null | jq -r '.count' 2>/dev/null || echo "0")

echo "Log counts:"
echo -e "  USER_ACTION:   ${GREEN}${USER_ACTIONS}${NC}"
echo -e "  HEALTH_CHECK:  ${YELLOW}${HEALTH_CHECKS}${NC}"
echo -e "  SYSTEM:        ${YELLOW}${SYSTEM_LOGS}${NC}"
echo ""

# Summary
echo "===================================="
echo "📊 Test Summary"
echo "===================================="

if [ "$USER_ACTIONS" -gt "0" ] || [ "$HEALTH_CHECKS" -gt "0" ]; then
    echo -e "${GREEN}✓ Log classification is working!${NC}"
    echo ""
    echo "Next steps:"
    echo "  1. View logs in Kibana: http://localhost:5601"
    echo "  2. Create filter: log_type: \"USER_ACTION\""
    echo "  3. Test with authenticated requests to see USER_ACTION logs"
else
    echo -e "${YELLOW}⚠ Services running but no classified logs yet${NC}"
    echo ""
    echo "Possible reasons:"
    echo "  - Services just started (wait 1-2 minutes)"
    echo "  - Need to restart services with: ./dev.sh full restart"
    echo "  - Fluent Bit not processing logs yet"
fi

echo ""
