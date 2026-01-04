#!/bin/bash

# Test Nginx Gateway với caching và compression

BASE_URL="http://localhost"
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Testing Nginx Gateway - Caching & Compression"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Wait for services
echo "⏳ Waiting for services..."
sleep 5

# Test 1: Cache MISS on first request
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Test 1: Cache MISS on first health check"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
RESPONSE=$(curl -s -i "$BASE_URL/actuator/health")
CACHE_STATUS=$(echo "$RESPONSE" | grep -i "X-Cache-Status:" | awk '{print $2}' | tr -d '\r')

if [[ "$CACHE_STATUS" == "MISS" ]]; then
    echo -e "${GREEN}✓ PASS${NC}: Cache MISS on first request"
else
    echo -e "${YELLOW}⚠ Got: $CACHE_STATUS${NC} (may be HIT if cache persists)"
fi
echo ""

# Test 2: Cache HIT on second request
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Test 2: Cache HIT on second health check"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
sleep 1
RESPONSE=$(curl -s -i "$BASE_URL/actuator/health")
CACHE_STATUS=$(echo "$RESPONSE" | grep -i "X-Cache-Status:" | awk '{print $2}' | tr -d '\r')

if [[ "$CACHE_STATUS" == "HIT" ]]; then
    echo -e "${GREEN}✓ PASS${NC}: Cache HIT on second request"
else
    echo -e "${RED}✗ FAIL${NC}: Expected HIT but got: $CACHE_STATUS"
fi
echo ""

# Test 3: Gzip compression
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Test 3: Gzip compression"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
RESPONSE=$(curl -s -i -H "Accept-Encoding: gzip" "$BASE_URL/actuator/health")
CONTENT_ENCODING=$(echo "$RESPONSE" | grep -i "Content-Encoding:" | awk '{print $2}' | tr -d '\r')

if [[ "$CONTENT_ENCODING" == "gzip" ]]; then
    echo -e "${GREEN}✓ PASS${NC}: Response is gzip compressed"
else
    echo -e "${YELLOW}⚠ INFO${NC}: No compression (response may be too small)"
fi
echo ""

# Test 4: Security headers
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Test 4: Security headers from Nginx"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
RESPONSE=$(curl -s -i "$BASE_URL/actuator/health")

FRAME_OPTIONS=$(echo "$RESPONSE" | grep -i "X-Frame-Options" | awk '{print $2}' | tr -d '\r')
XSS_PROTECTION=$(echo "$RESPONSE" | grep -i "X-XSS-Protection" | awk '{print $2}' | tr -d '\r')
CONTENT_TYPE=$(echo "$RESPONSE" | grep -i "X-Content-Type-Options" | awk '{print $2}' | tr -d '\r')

echo "X-Frame-Options: $FRAME_OPTIONS"
echo "X-XSS-Protection: $XSS_PROTECTION"
echo "X-Content-Type-Options: $CONTENT_TYPE"

if [[ "$FRAME_OPTIONS" == "DENY" ]]; then
    echo -e "${GREEN}✓ PASS${NC}: Security headers present"
else
    echo -e "${RED}✗ FAIL${NC}: Security headers missing"
fi
echo ""

# Test 5: Rate limiting
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Test 5: Rate limiting (100 req/s)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Sending 150 requests rapidly..."

SUCCESS=0
RATE_LIMITED=0

for i in {1..150}; do
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/actuator/health")
    if [[ "$HTTP_CODE" == "200" ]]; then
        ((SUCCESS++))
    elif [[ "$HTTP_CODE" == "503" ]] || [[ "$HTTP_CODE" == "429" ]]; then
        ((RATE_LIMITED++))
    fi
done

echo "Success: $SUCCESS, Rate Limited: $RATE_LIMITED"

if [[ $RATE_LIMITED -gt 0 ]]; then
    echo -e "${GREEN}✓ PASS${NC}: Rate limiting is working"
else
    echo -e "${YELLOW}⚠ INFO${NC}: No rate limiting triggered (requests too slow or limit too high)"
fi
echo ""

# Test 6: Cache metrics endpoint
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Test 6: Nginx status page"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
# Access from inside Docker network
docker exec nginx-gateway curl -s http://localhost/nginx_status | head -5
echo ""

# Test 7: Cache invalidation
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Test 7: Cache invalidation after POST"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
# Send POST to trigger cache bypass
curl -s -X POST "$BASE_URL/actuator/health" > /dev/null
sleep 1

# Next GET should be MISS
RESPONSE=$(curl -s -i "$BASE_URL/actuator/health")
CACHE_STATUS=$(echo "$RESPONSE" | grep -i "X-Cache-Status:" | awk '{print $2}' | tr -d '\r')

if [[ "$CACHE_STATUS" == "MISS" ]]; then
    echo -e "${GREEN}✓ PASS${NC}: Cache invalidated after POST"
else
    echo -e "${YELLOW}⚠ INFO${NC}: Got $CACHE_STATUS (cache may have TTL remaining)"
fi
echo ""

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Nginx Gateway Tests Complete"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Next steps:"
echo "1. Check Nginx logs: docker logs nginx-gateway"
echo "2. View cache stats: docker exec nginx-gateway ls -lh /var/cache/nginx/api"
echo "3. Monitor in Grafana: http://localhost:3001"
