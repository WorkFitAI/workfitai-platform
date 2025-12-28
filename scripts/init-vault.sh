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
create_service_policy "api-gateway"
create_service_policy "notification-service"
create_service_policy "recommendation-engine"

# Create secrets for auth-service
echo ""
echo "🔐 Populating secrets..."
create_service_secrets "auth-service" '{
  "data": {
    "jwt.access.expiration": "1800000",
    "jwt.refresh.expiration": "604800000",
    "mongodb.uri": "mongodb://auth-mongo:27017/auth-db",
    "redis.host": "auth-redis",
    "redis.port": "6379",
    "app.frontend.base-url": "'"${APP_BACKEND_BASE_URL:-http://localhost:3000}"'",
    "app.backend.base-url": "'"${APP_BACKEND_BASE_URL:-http://localhost:9085}"'",
    "app.session.max-sessions-per-user": "'"${MAX_SESSIONS_PER_USER:-5}"'",
    "oauth2.google.client-id": "'"${GOOGLE_CLIENT_ID:-your-google-client-id.apps.googleusercontent.com}"'",
    "oauth2.google.client-secret": "'"${GOOGLE_CLIENT_SECRET:-your-google-client-secret}"'",
    "oauth2.linkedin.client-id": "'"${LINKEDIN_CLIENT_ID:-your-linkedin-client-id}"'",
    "oauth2.linkedin.client-secret": "'"${LINKEDIN_CLIENT_SECRET:-your-linkedin-client-secret}"'",
    "oauth2.github.client-id": "'"${GITHUB_CLIENT_ID:-your-github-client-id}"'",
    "oauth2.github.client-secret": "'"${GITHUB_CLIENT_SECRET:-your-github-client-secret}"'",
    "oauth2.token-encryption-key": "'"${OAUTH2_TOKEN_ENCRYPTION_KEY:-changeme-generate-a-secure-256-bit-key-here}"'"
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
    "cloudinary.api-secret": "'"${CLOUDINARY_API_SECRET:-s_nMSKl3232BzSN1USpCa57axXw}"'",
    "app.account.deactivation-retention-days": "'"${DEACTIVATION_RETENTION_DAYS:-30}"'",
    "app.account.deletion-grace-period-days": "'"${DELETION_GRACE_PERIOD_DAYS:-7}"'"
  }
}'

# Create secrets for job-service
create_service_secrets "job-service" '{
  "data": {
    "DATASOURCE_URL": "jdbc:postgresql://job-postgres:5432/job_db",
    "DATASOURCE_USERNAME": "user",
    "DATASOURCE_PASSWORD": "job@123"
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
    "minio.bucket": "cvs-files",
    "spring.kafka.bootstrap-servers": "kafka:29092",
    "spring.kafka.producer.key-serializer": "org.apache.kafka.common.serialization.StringSerializer",
    "spring.kafka.producer.value-serializer": "org.springframework.kafka.support.serializer.JsonSerializer"
  }
}'

# Create secrets for application-service
create_service_secrets "application-service" '{
  "data": {
    "spring.data.mongodb.uri": "mongodb://application-mongo:27017/app-db"
  }
}'

# Create secrets for api-gateway
create_service_secrets "api-gateway" '{
  "data": {
    "app.cors.allowed-origins.local": "'"${ALLOWED_ORIGINS_LOCAL:-http://localhost:3000,http://localhost:3001}"'",
    "app.cors.allowed-origins.docker": "'"${ALLOWED_ORIGINS_DOCKER:-http://localhost:3000,http://localhost:3001}"'",
    "app.cors.allowed-origins.production": "'"${ALLOWED_ORIGINS:-}"'",
    "app.cors.websocket-origins.local": "'"${WS_ALLOWED_ORIGINS_LOCAL:-http://localhost:3000,http://localhost:3001}"'",
    "app.cors.websocket-origins.docker": "'"${WS_ALLOWED_ORIGINS_DOCKER:-http://localhost:3000,http://localhost:3001}"'",
    "app.cors.websocket-origins.production": "'"${WS_ALLOWED_ORIGINS:-}"'",
    "app.rate-limit.global.enabled": "'"${RATE_LIMIT_GLOBAL_ENABLED:-true}"'",
    "app.rate-limit.global.requests-per-second": "'"${RATE_LIMIT_GLOBAL_RPS:-100}"'",
    "app.rate-limit.global.burst-capacity": "'"${RATE_LIMIT_GLOBAL_BURST:-200}"'",
    "app.rate-limit.per-user.enabled": "'"${RATE_LIMIT_PER_USER_ENABLED:-true}"'",
    "app.rate-limit.per-user.requests-per-second": "'"${RATE_LIMIT_PER_USER_RPS:-50}"'",
    "app.rate-limit.per-user.burst-capacity": "'"${RATE_LIMIT_PER_USER_BURST:-100}"'",
    "app.cache.enabled": "'"${API_GATEWAY_CACHE_ENABLED:-true}"'",
    "app.cache.job-ttl-minutes": "'"${API_GATEWAY_CACHE_JOB_TTL_MINUTES:-5}"'",
    "app.cache.cv-ttl-minutes": "'"${API_GATEWAY_CACHE_CV_TTL_MINUTES:-10}"'",
    "app.cache.health-ttl-seconds": "'"${API_GATEWAY_CACHE_HEALTH_TTL_SECONDS:-30}"'",
    "redis.host": "api-redis",
    "redis.port": "6379",
    "app.frontend.base-url": "'"${APP_BACKEND_BASE_URL:-http://localhost:3000}"'",
    "app.backend.base-url": "'"${APP_BACKEND_BASE_URL:-http://localhost:9085}"'"
  }
}'

# Create secrets for notification-service
create_service_secrets "notification-service" '{
  "data": {
    "spring.data.mongodb.uri": "mongodb://notif-mongo:27017/notification-db",
    "spring.mail.host": "'"${EMAIL_SMTP_HOST:-smtp.gmail.com}"'",
    "spring.mail.port": "'"${EMAIL_SMTP_PORT:-587}"'",
    "spring.mail.username": "'"${EMAIL_ADDRESS:-aglaeahsr.0802@gmail.com}"'",
    "spring.mail.password": "'"${EMAIL_APP_PASSWORD:-msfjwckuuqhiifia}"'",
    "app.mail.from": "'"${MAIL_FROM:-noreply@workfitai.com}"'",
    "app.mail.from-name": "'"${MAIL_FROM_NAME:-WorkFitAI}"'"
  }
}'

# Create secrets for recommendation-engine
create_service_secrets "recommendation-engine" '{
  "data": {
    "model.path": "'"${RECOMMENDATION_MODEL_PATH:-/app/models/bi-encoder-e5-large}"'",
    "model.dimension": "'"${RECOMMENDATION_MODEL_DIMENSION:-1024}"'",
    "batch.size": "'"${RECOMMENDATION_BATCH_SIZE:-32}"'",
    "faiss.index.path": "/app/data/faiss_index",
    "faiss.index.type": "IndexFlatIP",
    "faiss.enable.persistence": "true",
    "kafka.bootstrap.servers": "kafka:29092",
    "kafka.consumer.group": "recommendation-engine",
    "kafka.topic.job-created": "job.created",
    "kafka.topic.job-updated": "job.updated",
    "kafka.topic.job-deleted": "job.deleted",
    "kafka.auto.offset.reset": "earliest",
    "kafka.enable.consumer": "'"${RECOMMENDATION_ENABLE_KAFKA:-true}"'",
    "job.service.url": "http://job-service:9082",
    "job.service.timeout": "30",
    "resume.max.size.mb": "'"${RECOMMENDATION_MAX_RESUME_SIZE_MB:-5}"'",
    "search.default.top-k": "'"${RECOMMENDATION_DEFAULT_TOP_K:-20}"'",
    "search.max.top-k": "'"${RECOMMENDATION_MAX_TOP_K:-100}"'",
    "search.min.similarity.score": "0.0",
    "cache.enable": "true",
    "cache.ttl.seconds": "3600",
    "workers.max": "4",
    "metrics.enable": "true",
    "metrics.port": "9090",
    "sync.enable.initial": "'"${RECOMMENDATION_ENABLE_INITIAL_SYNC:-true}"'",
    "sync.initial.batch.size": "50",
    "rebuild.enable.periodic": "false",
    "rebuild.interval.hours": "24"
  }
}'

echo ""
echo "✅ Vault initialization completed successfully!"
echo ""
echo "📊 Summary:"
echo "  - KV v2 secrets engine: enabled"
echo "  - Service policies: 8 created"
echo "  - Service secrets: 8 populated"
echo ""
echo "🔒 All services can now use token 'dev-token' to read their secrets"
echo "   (In production, each service should have its own token with restricted policy)"
