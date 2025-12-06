#!/bin/bash
# =============================================================================
# WorkFitAI - Registration Flow Test Script
# Tests through API Gateway (port 8088)
# =============================================================================

set -e

BASE_URL="${BASE_URL:-http://localhost:8088}"
ADMIN_EMAIL="admin@workfitai.com"
ADMIN_PASSWORD="admin123"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# =============================================================================
# Helper Functions
# =============================================================================

make_request() {
    local method=$1
    local endpoint=$2
    local data=$3
    local token=$4
    local extra_header=$5
    
    local headers="-H 'Content-Type: application/json'"
    if [ -n "$token" ]; then
        headers="$headers -H 'Authorization: Bearer $token'"
    fi
    if [ -n "$extra_header" ]; then
        headers="$headers -H '$extra_header'"
    fi
    
    if [ -n "$data" ]; then
        eval "curl -s -X $method $headers -d '$data' '${BASE_URL}${endpoint}'"
    else
        eval "curl -s -X $method $headers '${BASE_URL}${endpoint}'"
    fi
}

get_otp_from_redis() {
    local email=$1
    docker exec -it auth-redis redis-cli GET "auth:otp:$email" 2>/dev/null | tr -d '"' || echo ""
}

wait_for_otp() {
    local email=$1
    local max_wait=30
    local waited=0
    
    log_info "Waiting for OTP for $email..."
    while [ $waited -lt $max_wait ]; do
        local otp=$(get_otp_from_redis "$email")
        if [ -n "$otp" ] && [ "$otp" != "nil" ]; then
            echo "$otp"
            return 0
        fi
        sleep 1
        waited=$((waited + 1))
    done
    log_error "Timeout waiting for OTP"
    return 1
}

# =============================================================================
# Test Functions
# =============================================================================

test_admin_login() {
    log_info "Testing Admin Login..."
    
    local response=$(make_request "POST" "/api/v1/auth/login" \
        "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}")
    
    ADMIN_TOKEN=$(echo "$response" | jq -r '.data.accessToken // .accessToken // empty')
    ADMIN_USER_ID=$(echo "$response" | jq -r '.data.userId // .data.id // .userId // .id // empty')
    
    if [ -n "$ADMIN_TOKEN" ]; then
        log_success "Admin login successful. Token: ${ADMIN_TOKEN:0:50}..."
        return 0
    else
        log_error "Admin login failed: $response"
        return 1
    fi
}

test_candidate_flow() {
    log_info "========== CANDIDATE FLOW =========="
    
    local email="candidate_$(date +%s)@example.com"
    local username="candidate_$(date +%s)"
    
    # Step 1: Register
    log_info "1. Registering CANDIDATE: $email"
    local reg_response=$(make_request "POST" "/api/v1/auth/register" \
        "{\"username\":\"$username\",\"email\":\"$email\",\"password\":\"password123\",\"fullName\":\"Test Candidate\",\"phoneNumber\":\"0901234567\",\"role\":\"CANDIDATE\"}")
    
    if echo "$reg_response" | grep -q -i "error\|fail"; then
        log_error "Registration failed: $reg_response"
        return 1
    fi
    log_success "Registration response: $(echo $reg_response | jq -c '.')"
    
    # Step 2: Get OTP
    log_info "2. Waiting for OTP..."
    sleep 2
    local otp=$(get_otp_from_redis "$email")
    
    if [ -z "$otp" ] || [ "$otp" = "nil" ]; then
        log_warn "Could not get OTP from Redis. Check auth-service logs."
        log_info "Run: docker exec -it auth-redis redis-cli GET 'auth:otp:$email'"
        read -p "Enter OTP manually: " otp
    fi
    
    log_info "OTP: $otp"
    
    # Step 3: Verify OTP
    log_info "3. Verifying OTP..."
    local verify_response=$(make_request "POST" "/api/v1/auth/verify-otp" \
        "{\"email\":\"$email\",\"otp\":\"$otp\"}")
    
    log_success "Verify response: $(echo $verify_response | jq -c '.')"
    
    # Step 4: Login
    log_info "4. Logging in (should succeed for CANDIDATE)..."
    local login_response=$(make_request "POST" "/api/v1/auth/login" \
        "{\"email\":\"$email\",\"password\":\"password123\"}")
    
    local token=$(echo "$login_response" | jq -r '.data.accessToken // .accessToken // empty')
    
    if [ -n "$token" ]; then
        log_success "CANDIDATE login successful! Token: ${token:0:50}..."
    else
        log_error "CANDIDATE login failed: $login_response"
        return 1
    fi
    
    log_success "========== CANDIDATE FLOW COMPLETED =========="
}

test_hrmanager_flow() {
    log_info "========== HR_MANAGER FLOW =========="
    
    local email="hrm_$(date +%s)@newcorp.com"
    local username="hrm_$(date +%s)"
    
    # Step 1: Register HR_MANAGER with Company
    log_info "1. Registering HR_MANAGER with Company: $email"
    local reg_response=$(make_request "POST" "/api/v1/auth/register" \
        "{\"username\":\"$username\",\"email\":\"$email\",\"password\":\"password123\",\"fullName\":\"HR Manager New Corp\",\"phoneNumber\":\"0912345678\",\"role\":\"HR_MANAGER\",\"hrProfile\":{\"department\":\"Human Resources\",\"address\":\"123 Main Street\"},\"company\":{\"companyName\":\"New Corp $(date +%s)\",\"industry\":\"Technology\",\"companySize\":\"50-100\",\"website\":\"https://newcorp.com\",\"address\":\"456 Business Ave\"}}")
    
    log_success "Registration response: $(echo $reg_response | jq -c '.')"
    
    # Step 2: Get OTP
    log_info "2. Waiting for OTP..."
    sleep 2
    local otp=$(get_otp_from_redis "$email")
    
    if [ -z "$otp" ] || [ "$otp" = "nil" ]; then
        log_warn "Could not get OTP from Redis."
        read -p "Enter OTP manually: " otp
    fi
    
    log_info "OTP: $otp"
    
    # Step 3: Verify OTP
    log_info "3. Verifying OTP..."
    local verify_response=$(make_request "POST" "/api/v1/auth/verify-otp" \
        "{\"email\":\"$email\",\"otp\":\"$otp\"}")
    
    log_success "Verify response: $(echo $verify_response | jq -c '.')"
    
    # Step 4: Try Login (should FAIL - WAIT_APPROVED)
    log_info "4. Attempting login (should fail - WAIT_APPROVED)..."
    local login_response=$(make_request "POST" "/api/v1/auth/login" \
        "{\"email\":\"$email\",\"password\":\"password123\"}")
    
    local token=$(echo "$login_response" | jq -r '.data.accessToken // .accessToken // empty')
    
    if [ -z "$token" ]; then
        log_success "Expected: Login blocked (WAIT_APPROVED status)"
    else
        log_warn "Unexpected: Login succeeded without approval"
    fi
    
    # Step 5: Admin Approve
    log_info "5. Admin approving HR_MANAGER..."
    if [ -z "$ADMIN_TOKEN" ]; then
        test_admin_login
    fi
    
    local approve_response=$(curl -s -X POST \
        -H "Authorization: Bearer $ADMIN_TOKEN" \
        -H "X-Approver-Id: $ADMIN_USER_ID" \
        "${BASE_URL}/api/v1/hr/username/$username/approve-manager")
    
    log_success "Approval response: $(echo $approve_response | jq -c '.')"
    
    # Step 6: Wait for Kafka sync
    log_info "6. Waiting for Kafka sync (3s)..."
    sleep 3
    
    # Step 7: Login (should SUCCEED)
    log_info "7. Logging in after approval..."
    login_response=$(make_request "POST" "/api/v1/auth/login" \
        "{\"email\":\"$email\",\"password\":\"password123\"}")
    
    token=$(echo "$login_response" | jq -r '.data.accessToken // .accessToken // empty')
    
    if [ -n "$token" ]; then
        log_success "HR_MANAGER login successful after approval!"
        HRM_TOKEN="$token"
        HRM_USERNAME="$username"
    else
        log_error "HR_MANAGER login failed after approval: $login_response"
        return 1
    fi
    
    log_success "========== HR_MANAGER FLOW COMPLETED =========="
}

# =============================================================================
# Main
# =============================================================================

main() {
    echo "=============================================="
    echo "   WorkFitAI Registration Flow Test"
    echo "   API Gateway: $BASE_URL"
    echo "=============================================="
    echo ""
    
    # Check dependencies
    if ! command -v jq &> /dev/null; then
        log_error "jq is required. Install with: brew install jq"
        exit 1
    fi
    
    # Check services
    log_info "Checking API Gateway health..."
    local health=$(curl -s "${BASE_URL}/actuator/health" 2>/dev/null || echo "")
    if [ -z "$health" ]; then
        log_error "API Gateway not responding at $BASE_URL"
        exit 1
    fi
    log_success "API Gateway is healthy"
    
    echo ""
    echo "Select test to run:"
    echo "1) Admin Login Only"
    echo "2) CANDIDATE Flow (auto-activate)"
    echo "3) HR_MANAGER Flow (admin approval)"
    echo "4) Full Test (all flows)"
    echo "5) Exit"
    read -p "Choice [1-5]: " choice
    
    case $choice in
        1)
            test_admin_login
            ;;
        2)
            test_candidate_flow
            ;;
        3)
            test_hrmanager_flow
            ;;
        4)
            test_admin_login
            echo ""
            test_candidate_flow
            echo ""
            test_hrmanager_flow
            ;;
        5)
            exit 0
            ;;
        *)
            log_error "Invalid choice"
            exit 1
            ;;
    esac
    
    echo ""
    log_success "Test completed!"
}

main "$@"
