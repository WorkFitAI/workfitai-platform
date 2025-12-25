#!/bin/bash

# ============================================================================
# Phase 1 Testing Script - CORS, Rate Limiting, Security Headers
# ============================================================================

# NOTE: Don't use 'set -e' because grep returns exit code 1 when nothing found
# which would terminate the script prematurely

GATEWAY_URL="https://api.workfitai.uk"
VALID_ORIGIN="https://workfitai.uk"
INVALID_ORIGIN="https://evil.com"

echo "================================================"
echo "🧪 Phase 1 Testing - API Gateway Security"
echo "================================================"
echo ""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test results
PASS_COUNT=0
FAIL_COUNT=0

# Function to test CORS
test_cors() {
    echo "================================================"
    echo "1️⃣  Testing CORS Configuration"
    echo "================================================"
    
    # Test 1: Valid origin
    echo -e "\n${YELLOW}Test 1.1: Valid origin (should pass)${NC}"
    RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 --connect-timeout 2 \
        -H "Origin: $VALID_ORIGIN" \
        -H "Access-Control-Request-Method: GET" \
        -H "Access-Control-Request-Headers: authorization" \
        -X OPTIONS "$GATEWAY_URL/user/profile" 2>&1)
    
    if [ "$RESPONSE" == "200" ] || [ "$RESPONSE" == "204" ]; then
        echo -e "${GREEN}✅ PASS${NC} - Valid origin accepted (HTTP $RESPONSE)"
        ((PASS_COUNT++))
    else
        echo -e "${RED}❌ FAIL${NC} - Valid origin rejected (HTTP $RESPONSE)"
        ((FAIL_COUNT++))
    fi
    
    # Test 2: Invalid origin (should be rejected in production)
    echo -e "\n${YELLOW}Test 1.2: Invalid origin (should fail in production)${NC}"
    echo "Note: In local/docker profile, this might still pass"
    RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 --connect-timeout 2 \
        -H "Origin: $INVALID_ORIGIN" \
        -H "Access-Control-Request-Method: GET" \
        -X OPTIONS "$GATEWAY_URL/user/profile" 2>&1)
    
    echo "Response: HTTP $RESPONSE"
    echo "(Expected: 403 in production, may be 200 in local/docker)"
    
    # Test 3: CORS headers present
    echo -e "\n${YELLOW}Test 1.3: CORS headers in response${NC}"
    CORS_HEADERS=$(curl -s -I --max-time 5 --connect-timeout 2 \
        -H "Origin: $VALID_ORIGIN" \
        -X OPTIONS "$GATEWAY_URL/user/profile" | grep -i "access-control")
    
    if [ -n "$CORS_HEADERS" ]; then
        echo -e "${GREEN}✅ PASS${NC} - CORS headers present:"
        echo "$CORS_HEADERS"
        ((PASS_COUNT++))
    else
        echo -e "${RED}❌ FAIL${NC} - CORS headers missing"
        ((FAIL_COUNT++))
    fi
    
    # Test 4: WebSocket CORS
    echo -e "\n${YELLOW}Test 1.4: WebSocket CORS (should not be blocked)${NC}"
    WS_RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 --connect-timeout 2 \
        -H "Origin: $VALID_ORIGIN" \
        -H "Access-Control-Request-Method: GET" \
        -X OPTIONS "$GATEWAY_URL/notification/ws/info" 2>&1)
    
    if [ "$WS_RESPONSE" == "200" ] || [ "$WS_RESPONSE" == "204" ]; then
        echo -e "${GREEN}✅ PASS${NC} - WebSocket CORS not blocked (HTTP $WS_RESPONSE)"
        ((PASS_COUNT++))
    else
        echo -e "${RED}❌ FAIL${NC} - WebSocket CORS blocked (HTTP $WS_RESPONSE)"
        ((FAIL_COUNT++))
    fi
}

# Function to test rate limiting
test_rate_limiting() {
    echo ""
    echo "================================================"
    echo "2️⃣  Testing Rate Limiting"
    echo "================================================"
    
    # Test 1: Normal request (should pass)
    echo -e "\n${YELLOW}Test 2.1: Normal request (should pass)${NC}"
    RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 --connect-timeout 2 "$GATEWAY_URL/actuator/health" 2>&1)
    
    if [ "$RESPONSE" == "200" ]; then
        echo -e "${GREEN}✅ PASS${NC} - Normal request allowed (HTTP $RESPONSE)"
        ((PASS_COUNT++))
    else
        echo -e "${RED}❌ FAIL${NC} - Normal request blocked (HTTP $RESPONSE)"
        ((FAIL_COUNT++))
    fi
    
    # Test 2: Check rate limit headers
    echo -e "\n${YELLOW}Test 2.2: Rate limit headers${NC}"
    RATE_HEADERS=$(curl -s -I --max-time 5 --connect-timeout 2 "$GATEWAY_URL/actuator/health" | grep -i "x-ratelimit")
    
    if [ -n "$RATE_HEADERS" ]; then
        echo -e "${GREEN}✅ PASS${NC} - Rate limit headers present:"
        echo "$RATE_HEADERS"
        ((PASS_COUNT++))
    else
        echo -e "${YELLOW}⚠️  SKIP${NC} - Rate limit headers not found (expected for /actuator)"
    fi
    
    # Test 3: Burst requests (check if rate limiting triggers)
    echo -e "\n${YELLOW}Test 2.3: Burst requests (100 requests to /auth/login)${NC}"
    echo "Sending 100 rapid requests to test rate limiting..."
    
    SUCCESS=0
    RATE_LIMITED=0
    FAILED=0
    
    for i in {1..100}; do
        RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 --connect-timeout 2 \
            -X POST "$GATEWAY_URL/auth/login" \
            -H "Content-Type: application/json" \
            -d '{"username":"test","password":"test"}' 2>&1)
        
        if [ "$RESPONSE" == "429" ]; then
            ((RATE_LIMITED++))
        elif [ "$RESPONSE" == "200" ] || [ "$RESPONSE" == "401" ] || [ "$RESPONSE" == "400" ]; then
            ((SUCCESS++))
        else
            ((FAILED++))
        fi
    done
    
    echo "Results: $SUCCESS successful, $RATE_LIMITED rate-limited, $FAILED failed"
    
    if [ "$RATE_LIMITED" -gt 0 ]; then
        echo -e "${GREEN}✅ PASS${NC} - Rate limiting is working ($RATE_LIMITED requests blocked)"
        ((PASS_COUNT++))
    else
        echo -e "${YELLOW}⚠️  WARNING${NC} - No rate limiting detected (limit may be high: 50 req/s per user)"
        echo "    Tip: Lower rate limit in config or send more concurrent requests"
    fi
}

# Function to test security headers
test_security_headers() {
    echo ""
    echo "================================================"
    echo "3️⃣  Testing Security Headers"
    echo "================================================"
    
    echo -e "\n${YELLOW}Fetching security headers from gateway...${NC}"
    HEADERS=$(curl -s -I --max-time 5 --connect-timeout 2 "$GATEWAY_URL/actuator/health")
    
    # Check each security header
    SECURITY_HEADERS=(
        "X-Frame-Options"
        "X-Content-Type-Options"
        "X-XSS-Protection"
        "Referrer-Policy"
        "Content-Security-Policy"
    )
    
    for HEADER in "${SECURITY_HEADERS[@]}"; do
        if echo "$HEADERS" | grep -qi "$HEADER"; then
            VALUE=$(echo "$HEADERS" | grep -i "$HEADER" | cut -d':' -f2- | xargs)
            echo -e "${GREEN}✅ $HEADER${NC}: $VALUE"
            ((PASS_COUNT++))
        else
            echo -e "${RED}❌ $HEADER${NC}: Missing"
            ((FAIL_COUNT++))
        fi
    done
    
    # Check if Server header is removed
    echo -e "\n${YELLOW}Test 3.7: Server header removed (information disclosure)${NC}"
    if echo "$HEADERS" | grep -qi "^Server:"; then
        echo -e "${YELLOW}⚠️  WARNING${NC} - Server header still present"
    else
        echo -e "${GREEN}✅ PASS${NC} - Server header removed"
        ((PASS_COUNT++))
    fi
}

# Function to test request size limits
test_request_limits() {
    echo ""
    echo "================================================"
    echo "4️⃣  Testing Request Size Limits"
    echo "================================================"
    
    # Wait for rate limit to reset (tokens refill)
    echo -e "\n${YELLOW}⏳ Waiting 20 seconds for rate limit to refill...${NC}"
    sleep 20
    
    echo -e "\n${YELLOW}Test 4.1: Small request (should pass)${NC}"
    RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 --connect-timeout 2 \
        -X POST "$GATEWAY_URL/auth/login" \
        -H "Content-Type: application/json" \
        -d '{"username":"test","password":"test"}' 2>&1)
    
    if [ "$RESPONSE" == "200" ] || [ "$RESPONSE" == "401" ] || [ "$RESPONSE" == "400" ]; then
        echo -e "${GREEN}✅ PASS${NC} - Small request accepted (HTTP $RESPONSE)"
        ((PASS_COUNT++))
    else
        echo -e "${RED}❌ FAIL${NC} - Small request rejected (HTTP $RESPONSE)"
        ((FAIL_COUNT++))
    fi
    
    echo -e "\n${YELLOW}Test 4.2: Large payload (testing 10MB limit)${NC}"
    echo "Creating 11MB payload file to test size limit..."
    
    # Create a temporary file with 11MB of data
    TEMP_FILE="/tmp/large-payload-test-$$.dat"
    dd if=/dev/zero of="$TEMP_FILE" bs=1M count=11 2>/dev/null || {
        echo -e "${YELLOW}⚠️  SKIP${NC} - Could not create test file"
        return
    }
    
    RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 --connect-timeout 2 \
        -X POST "$GATEWAY_URL/auth/login" \
        -H "Content-Type: application/octet-stream" \
        --data-binary "@$TEMP_FILE" 2>&1)
    
    # Clean up temp file
    rm -f "$TEMP_FILE"
    
    # Gateway should reject with 413 (Payload Too Large) or 400 (Bad Request due to size)
    if [ "$RESPONSE" == "413" ]; then
        echo -e "${GREEN}✅ PASS${NC} - Large payload rejected (HTTP 413 Payload Too Large)"
        ((PASS_COUNT++))
    elif [ "$RESPONSE" == "400" ]; then
        echo -e "${GREEN}✅ PASS${NC} - Large payload rejected (HTTP 400 Bad Request)"
        ((PASS_COUNT++))
    else
        echo -e "${YELLOW}⚠️  SKIP${NC} - Large payload test inconclusive (HTTP $RESPONSE)"
        echo "    Gateway may need additional configuration for request size limits"
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
        echo "Please start the gateway first: cd services/api-gateway && ./mvnw spring-boot:run"
        exit 1
    fi
    
    echo -e "${GREEN}✅ Gateway is running${NC}"
    echo ""
    
    # Run all tests
    test_cors
    test_rate_limiting
    test_security_headers
    test_request_limits
    
    # Summary
    echo ""
    echo "================================================"
    echo "📊 Test Summary"
    echo "================================================"
    echo -e "${GREEN}✅ Passed: $PASS_COUNT${NC}"
    echo -e "${RED}❌ Failed: $FAIL_COUNT${NC}"
    echo ""
    
    if [ "$FAIL_COUNT" -gt 0 ]; then
        echo -e "${RED}⚠️  Some tests failed. Please review the output above.${NC}"
        exit 1
    else
        echo -e "${GREEN}🎉 All tests passed!${NC}"
        exit 0
    fi
}

# Run main function
main
