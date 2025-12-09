#!/bin/bash

# Wait for Vault to be ready
echo "Waiting for Vault to be ready..."
sleep 10

# Set Vault address and token
export VAULT_ADDR=http://vault:8200
export VAULT_TOKEN=dev-token

# Enable KV secrets engine if not already enabled
vault secrets enable -version=2 -path=secret kv || true

# Create secrets for each service
echo "Creating secrets for auth-service..."
vault kv put secret/auth-service \
  jwt.secret=CJmTxK/y/bJPsho/gIxQofHzv3nj+FoABHTSNsBwqCTy2bN3TavCklalHVGw/6KX \
  jwt.access.expiration=900000 \
  jwt.refresh.expiration=604800000 \
  mongodb.uri=mongodb://auth-mongo:27017/auth-db \
  redis.host=auth-redis \
  redis.port=6379

echo "Creating secrets for user-service..."
vault kv put secret/user-service \
  datasource.url=jdbc:postgresql://user-postgres:5432/user_db \
  datasource.username=user \
  datasource.password=pass

echo "Creating secrets for api-gateway..."
vault kv put secret/api-gateway \
  auth.service.url=http://auth-service:9005 \
  redis.host=api-redis \
  redis.port=6379

echo "Creating secrets for job-service..."
vault kv put secret/job-service \
  datasource.url=jdbc:postgresql://job-postgres:5432/job_db \
  datasource.username=user \
  datasource.password=job@123

echo "Creating secrets for cv-service..."
vault kv put secret/cv-service \
  mongodb.uri=mongodb://user:123456@cv-mongo:27017/cv-db?authSource=cv-db

echo "Creating secrets for application-service..."
vault kv put secret/application-service \
  mongodb.uri=mongodb://application-mongo:27017/application_db \
  kafka.bootstrap-servers=kafka-workfitai:29092

echo "Creating secrets for monitoring-service..."
vault kv put secret/monitoring-service \
  redis.host=api-redis \
  redis.port=6379

echo "Vault secrets initialization completed!"