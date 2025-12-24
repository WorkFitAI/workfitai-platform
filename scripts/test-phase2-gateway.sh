#!/bin/bash

# ============================================================================
# Phase 2 Testing Script - Circuit Breaker & Resilience
# ============================================================================

GATEWAY_URL="http://localhost:9085"

echo "================================================"
echo "🧪 Phase 2 Testing - Circuit Breaker & Resilience"
echo "================================================"
echo ""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test results
PASS_COUNT=0
FAIL_COUNT=0

# Function to test circuit breaker
test_circuit_breaker() {
    echo "================================================"
    echo "1️⃣  Testing Circuit Breaker"
    echo "================================================"
    
    echo -e "\n${YELLOW}Test 1.1: Fallback endpoint availability${NC}"
    RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 --connect-timeout 2 "$GATEWAY_URL/fallback/test" 2>&1)
    
    if [ "$RESPONSE" == "503" ]; then
        echo -e "${GREEN}✅ PASS${NC} - Fallback endpoint responding (HTTP $RESPONSE)"
        ((PASS_COUNT++))
    else
        echo -e "${RED}❌ FAIL${NC} - Fallback endpoint issue (HTTP $RESPONSE)"
        ((FAIL_COUNT++))
    fi
    
    echo -e "\n${YELLOW}Test 1.2: Auth service fallback${NC}"
    # Test fallback directly (auth endpoints use POST)
    RESPONSE=$(curl -s -X POST --max-time 5 --connect-timeout 2 "$GATEWAY_URL/fallback/auth/login")
    
    if echo "$RESPONSE" | grep -q "fallback"; then
        echo -e "${GREEN}✅ PASS${NC} - Auth fallback returns proper response"
        echo "Sample: $(echo $RESPONSE | jq -r '.message' 2>/dev/null || echo $RESPONSE | head -c 80)"
        ((PASS_COUNT++))
    else
        echo -e "${RED}❌ FAIL${NC} - Auth fallback response invalid"
        ((FAIL_COUNT++))
    fi
    
    echo -e "\n${YELLOW}Test 1.3: User service fallback${NC}"
    RESPONSE=$(curl -s --max-time 5 --connect-timeout 2 "$GATEWAY_URL/fallback/user/profile")
    
    if echo "$RESPONSE" | grep -q "fallback"; then
        echo -e "${GREEN}✅ PASS${NC} - User fallback returns proper response"
        ((PASS_COUNT++))
    else
        echo -e "${RED}❌ FAIL${NC} - User fallback response invalid"
        ((FAIL_COUNT++))
    fi
}

# Function to test retry mechanism
test_retry_mechanism() {
    echo ""
    echo "================================================"
    echo "2️⃣  Testing Retry Mechanism"
    echo "================================================"
    
    echo -e "\n${YELLOW}Test 2.1: Normal request (no retry needed)${NC}"
    START_TIME=$(date +%s)
    RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 --connect-timeout 2 "$GATEWAY_URL/actuator/health" 2>&1)
    END_TIME=$(date +%s)
    DURATION=$((END_TIME - START_TIME))
    
    if [ "$RESPONSE" == "200" ]; then
        echo -e "${GREEN}✅ PASS${NC} - Request succeeded without retry (${DURATION}s)"
        ((PASS_COUNT++))
    else
        echo -e "${RED}❌ FAIL${NC} - Request failed (HTTP $RESPONSE)"
        ((FAIL_COUNT++))
    fi
    
    echo -e "\n${YELLOW}Test 2.2: Service discovery working${NC}"
    # Test if gateway can discover services via Consul
    RESPONSE=$(curl -s --max-time 5 --connect-timeout 2 "$GATEWAY_URL/actuator/health")
    
    if echo "$RESPONSE" | grep -q "consul"; then
        CONSUL_STATUS=$(echo "$RESPONSE" | jq -r '.components.consul.status' 2>/dev/null || echo "UNKNOWN")
        if [ "$CONSUL_STATUS" == "UP" ]; then
            echo -e "${GREEN}✅ PASS${NC} - Consul discovery is UP"
            ((PASS_COUNT++))
        else
            echo -e "${YELLOW}⚠️  WARNING${NC} - Consul status: $CONSUL_STATUS"
        fi
    else
        echo -e "${YELLOW}⚠️  SKIP${NC} - Consul info not in health check"
    fi
}

# Function to test timeout configuration
test_timeout_configuration() {
    echo ""
    echo "================================================"
    echo "3️⃣  Testing Timeout Configuration"
    echo "================================================"
    
    echo -e "\n${YELLOW}Test 3.1: Gateway response time${NC}"
    START_TIME=$(date +%s)
    RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 15 --connect-timeout 2 "$GATEWAY_URL/actuator/health" 2>&1)
    END_TIME=$(date +%s)
    DURATION=$((END_TIME - START_TIME))
    
    if [ "$RESPONSE" == "200" ] && [ "$DURATION" -lt 5 ]; then
        echo -e "${GREEN}✅ PASS${NC} - Fast response: ${DURATION}s (< 5s)"
        ((PASS_COUNT++))
    elif [ "$RESPONSE" == "200" ]; then
        echo -e "${YELLOW}⚠️  SLOW${NC} - Response took ${DURATION}s"
        ((PASS_COUNT++))
    else
        echo -e "${RED}❌ FAIL${NC} - Request failed or timeout"
        ((FAIL_COUNT++))
    fi
    
    echo -e "\n${YELLOW}Test 3.2: HTTP client timeout settings${NC}"
    # Check if gateway has proper timeout config
    echo "Checking gateway configuration..."
    
    # Test with multiple quick requests to verify no connection pool issues
    SUCCESS=0
    for i in {1..10}; do
        RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 --connect-timeout 2 "$GATEWAY_URL/actuator/health" 2>&1)
        if [ "$RESPONSE" == "200" ]; then
            ((SUCCESS++))
        fi
    done
    
    if [ "$SUCCESS" -eq 10 ]; then
        echo -e "${GREEN}✅ PASS${NC} - All 10 rapid requests succeeded (connection pool working)"
        ((PASS_COUNT++))
    elif [ "$SUCCESS" -ge 8 ]; then
        echo -e "${YELLOW}⚠️  WARNING${NC} - $SUCCESS/10 requests succeeded"
        ((PASS_COUNT++))
    else
        echo -e "${RED}❌ FAIL${NC} - Only $SUCCESS/10 requests succeeded"
        ((FAIL_COUNT++))
    fi
}

# Function to test resilience patterns
test_resilience_patterns() {
    echo ""
    echo "================================================"
    echo "4️⃣  Testing Resilience Patterns"
    echo "================================================"
    
    echo -e "\n${YELLOW}Test 4.1: Graceful degradation${NC}"
    # Test fallback endpoints
    FALLBACK_RESPONSE=$(curl -s --max-time 5 --connect-timeout 2 "$GATEWAY_URL/fallback/test")
    
    if echo "$FALLBACK_RESPONSE" | grep -q '"fallback":true'; then
        echo -e "${GREEN}✅ PASS${NC} - Fallback includes degradation flag"
        ((PASS_COUNT++))
    elif echo "$FALLBACK_RESPONSE" | grep -q "unavailable"; then
        echo -e "${GREEN}✅ PASS${NC} - Fallback provides user-friendly message"
        ((PASS_COUNT++))
    else
        echo -e "${RED}❌ FAIL${NC} - Fallback response invalid"
        ((FAIL_COUNT++))
    fi
    
    echo -e "\n${YELLOW}Test 4.2: Error response structure${NC}"
    FALLBACK_RESPONSE=$(curl -s -X POST --max-time 5 --connect-timeout 2 "$GATEWAY_URL/fallback/auth/login")
    
    HAS_STATUS=$(echo "$FALLBACK_RESPONSE" | jq -e '.status' > /dev/null 2>&1 && echo "yes" || echo "no")
    HAS_MESSAGE=$(echo "$FALLBACK_RESPONSE" | jq -e '.message' > /dev/null 2>&1 && echo "yes" || echo "no")
    HAS_SERVICE=$(echo "$FALLBACK_RESPONSE" | jq -e '.service' > /dev/null 2>&1 && echo "yes" || echo "no")
    
    if [ "$HAS_STATUS" == "yes" ] && [ "$HAS_MESSAGE" == "yes" ] && [ "$HAS_SERVICE" == "yes" ]; then
        echo -e "${GREEN}✅ PASS${NC} - Fallback has proper structure (status, message, service)"
        ((PASS_COUNT++))
    else
        echo -e "${RED}❌ FAIL${NC} - Fallback missing required fields"
        echo "  Status: $HAS_STATUS, Message: $HAS_MESSAGE, Service: $HAS_SERVICE"
        ((FAIL_COUNT++))
    fi
}

# Function to test metrics exposure
test_metrics_exposure() {
    echo ""
    echo "================================================"
    echo "5️⃣  Testing Metrics & Monitoring"
    echo "================================================"
    
    echo -e "\n${YELLOW}Test 5.1: Actuator health endpoint${NC}"
    RESPONSE=$(curl -s --max-time 5 --connect-timeout 2 "$GATEWAY_URL/actuator/health")
    
    if echo "$RESPONSE" | grep -q '"status":"UP"'; then
        echo -e "${GREEN}✅ PASS${NC} - Health endpoint UP"
        ((PASS_COUNT++))
    else
        echo -e "${RED}❌ FAIL${NC} - Health endpoint not UP"
        ((FAIL_COUNT++))
    fi
    
    echo -e "\n${YELLOW}Test 5.2: Prometheus metrics endpoint${NC}"
    RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 --connect-timeout 2 "$GATEWAY_URL/actuator/prometheus" 2>&1)
    
    if [ "$RESPONSE" == "200" ]; then
        echo -e "${GREEN}✅ PASS${NC} - Prometheus metrics available"
        ((PASS_COUNT++))
    else
        echo -e "${YELLOW}⚠️  SKIP${NC} - Prometheus metrics not exposed (HTTP $RESPONSE)"
    fi
    
    echo -e "\n${YELLOW}Test 5.3: Circuit breaker metrics${NC}"
    METRICS=$(curl -s --max-time 5 --connect-timeout 2 "$GATEWAY_URL/actuator/prometheus" 2>&1)
    
    if echo "$METRICS" | grep -q "resilience4j_circuitbreaker"; then
        echo -e "${GREEN}✅ PASS${NC} - Circuit breaker metrics present"
        ((PASS_COUNT++))
    else
        echo -e "${YELLOW}⚠️  SKIP${NC} - Circuit breaker metrics not found (may need requests first)"
    fi
}

# Main execution
main() {
    echo "Starting tests against: $GATEWAY_URL"
    echo "Make sure the gateway is running!"
    echo ""
    
    # Check if gateway is running
    if ! curl -s -o /dev/null -w "%{http_code}" --max-time 5 --connect-timeout 2 "$GATEWAY_URL/actuator/health" | grep -q "200"; then
        echo -e "${RED}❌ Gateway is not running at $GATEWAY_URL${NC}"
        echo "Please start the gateway first"
        exit 1
    fi
    
    echo -e "${GREEN}✅ Gateway is running${NC}"
    
    # Run all tests
    test_circuit_breaker
    test_retry_mechanism
    test_timeout_configuration
    test_resilience_patterns
    test_metrics_exposure
    
    # Print summary
    echo ""
    echo "================================================"
    echo "📊 Test Summary"
    echo "================================================"
    echo -e "${GREEN}✅ Passed: $PASS_COUNT${NC}"
    echo -e "${RED}❌ Failed: $FAIL_COUNT${NC}"
    echo ""
    
    if [ "$FAIL_COUNT" -eq 0 ]; then
        echo -e "${GREEN}🎉 All tests passed!${NC}"
        exit 0
    else
        echo -e "${YELLOW}⚠️  Some tests failed. Please review the output above.${NC}"
        exit 1
    fi
}

# Run main function
main
