#!/bin/bash
set -e

echo "🔐 Initializing Vault for WorkFitAI Platform..."

# Vault connection
VAULT_ADDR=${VAULT_ADDR:-"http://localhost:8200"}
ROOT_TOKEN=${VAULT_ROOT_TOKEN:-"dev-token"}

# Wait for Vault to be ready
echo "⏳ Waiting for Vault to be ready..."
timeout 30 sh -c 'until curl -s -o /dev/null -w "%{http_code}" http://vault:8200/v1/sys/health | grep -q "200\|429\|473\|501\|503"; do sleep 1; done' || {
    echo "❌ Vault is not ready after 30 seconds"
    exit 1
}

echo "✅ Vault is ready"

# Enable KV v2 secrets engine (idempotent)
echo "📦 Enabling KV v2 secrets engine at 'secret/'..."
curl -s -X POST \
    -H "X-Vault-Token: $ROOT_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"type":"kv","options":{"version":"2"}}' \
    "$VAULT_ADDR/v1/sys/mounts/secret" 2>&1 | grep -q "existing mount" && echo "ℹ️  Secret engine already exists" || echo "✅ Secret engine enabled"

# Create service-specific policies
create_service_policy() {
    local service_name=$1
    echo "📝 Creating policy for $service_name..."
    
    curl -s -X PUT \
        -H "X-Vault-Token: $ROOT_TOKEN" \
        -H "Content-Type: application/json" \
        -d "{
            \"policy\": \"path \\\"secret/data/$service_name\\\" { capabilities = [\\\"read\\\"] }\"
        }" \
        "$VAULT_ADDR/v1/sys/policies/acl/$service_name-policy"
    
    echo "✅ Policy created for $service_name"
}

# Create secrets for each service
create_service_secrets() {
    local service_name=$1
    shift
    local secrets_json=$@
    
    echo "🔑 Creating secrets for $service_name..."
    
    curl -s -X POST \
        -H "X-Vault-Token: $ROOT_TOKEN" \
        -H "Content-Type: application/json" \
        -d "$secrets_json" \
        "$VAULT_ADDR/v1/secret/data/$service_name"
    
    echo "✅ Secrets created for $service_name"
}

# Create policies for all services
echo ""
echo "📋 Creating service policies..."
create_service_policy "auth-service"
create_service_policy "user-service"
create_service_policy "job-service"
create_service_policy "cv-service"
create_service_policy "application-service"
create_service_policy "notification-service"

# Create secrets for auth-service
echo ""
echo "🔐 Populating secrets..."
create_service_secrets "auth-service" '{
  "data": {
    "jwt.access.expiration": "900000",
    "jwt.refresh.expiration": "604800000",
    "mongodb.uri": "mongodb://auth-mongo:27017/auth-db",
    "redis.host": "auth-redis",
    "redis.port": "6379"
  }
}'

# Create secrets for user-service
create_service_secrets "user-service" '{
  "data": {
    "spring.datasource.url": "jdbc:postgresql://user-postgres:5432/user_db",
    "spring.datasource.username": "user",
    "spring.datasource.password": "pass",
    "cloudinary.cloud-name": "'"${CLOUDINARY_CLOUD_NAME:-dphibwpag}"'",
    "cloudinary.api-key": "'"${CLOUDINARY_API_KEY:-586672837199227}"'",
    "cloudinary.api-secret": "'"${CLOUDINARY_API_SECRET:-s_nMSKl3232BzSN1USpCa57axXw}"'"
  }
}'

# Create secrets for job-service
create_service_secrets "job-service" '{
  "data": {
    "spring.datasource.url": "jdbc:postgresql://job-postgres:5432/job_db",
    "spring.datasource.username": "user",
    "spring.datasource.password": "job@123"
  }
}'

# Create secrets for cv-service
create_service_secrets "cv-service" '{
  "data": {
    "spring.data.mongodb.uri": "mongodb://user:123456@cv-mongo:27017/cv-db?authSource=cv-db",
    "spring.data.redis.host": "cv-redis",
    "spring.data.redis.port": "6379",
    "minio.endpoint": "http://minio:9000",
    "minio.access-key": "minioadmin",
    "minio.secret-key": "minioadmin",
    "minio.bucket": "cvs-files"
  }
}'

# Create secrets for application-service
create_service_secrets "application-service" '{
  "data": {
    "spring.data.mongodb.uri": "mongodb://application-mongo:27017/app-db"
  }
}'

# Create secrets for notification-service
create_service_secrets "notification-service" '{
  "data": {
    "spring.data.mongodb.uri": "mongodb://notif-mongo:27017/notification-db",
    "spring.mail.host": "'"${EMAIL_SMTP_HOST:-smtp.gmail.com}"'",
    "spring.mail.port": "'"${EMAIL_SMTP_PORT:-587}"'",
    "spring.mail.username": "'"${EMAIL_ADDRESS:-aglaeahsr.0802@gmail.com}"'",
    "spring.mail.password": "'"${EMAIL_APP_PASSWORD:-msfjwckuuqhiifia}"'"
  }
}'

echo ""
echo "✅ Vault initialization completed successfully!"
echo ""
echo "📊 Summary:"
echo "  - KV v2 secrets engine: enabled"
echo "  - Service policies: 6 created"
echo "  - Service secrets: 6 populated"
echo ""
echo "🔒 All services can now use token 'dev-token' to read their secrets"
echo "   (In production, each service should have its own token with restricted policy)"
