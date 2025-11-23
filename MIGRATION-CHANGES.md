# WorkFitAI Platform - Migration Changes & Setup Guide

## 📋 Tổng quan thay đổi

Tài liệu này mô tả chi tiết tất cả các thay đổi so với branch `dev` và hướng dẫn setup môi trường mới với Vault integration và monitoring system.

## 🚀 Các thay đổi chính

### 1. Infrastructure Changes

#### 1.1 HashiCorp Vault Integration
- **Thêm mới**: HashiCorp Vault 1.13.3 cho quản lý secrets tập trung
- **Port**: 8200
- **Token**: `dev-token` (development only)
- **KV Engine**: Version 2 (`secret/`)

#### 1.2 Redis Infrastructure Expansion
- **auth-redis**: Port 6379 (existing)
- **api-redis**: Port 6380 (existing) 
- **cv-redis**: Port 6381 (NEW - dedicated for CV service)

#### 1.3 Service Discovery với Consul
- **Consul**: Port 8500
- **Health checks**: 10s interval
- **Service registration**: Auto với Spring Cloud Consul

### 2. Microservices Changes

#### 2.1 API Gateway (Port 9085)
```yaml
# Thay đổi chính: Discovery Locator
spring:
  cloud:
    gateway:
      discovery:
        locator:
          enabled: true
          lower-case-service-id: true
```
- **Tính năng mới**: Auto-routing dựa trên Consul service registry
- **URL pattern**: `http://localhost:9085/{service-name}/{endpoint}`
- **Ví dụ**: `http://localhost:9085/auth/api/auth/login`

#### 2.2 Auth Service (Port 9080)
- **Vault integration**: `spring.config.import: optional:vault://secret/auth`
- **MongoDB**: auth-mongo:27017
- **Redis**: auth-redis:6379
- **Service name**: `auth` (chuẩn hóa từ `auth-service`)

#### 2.3 User Service (Port 9081)
- **Vault integration**: `spring.config.import: optional:vault://secret/user`
- **PostgreSQL**: user-postgres:5432
- **Service name**: `user` (chuẩn hóa từ `user-service`)

#### 2.4 Job Service (Port 9082) ⭐ NEW VAULT CONFIG
- **Vault integration**: `spring.config.import: optional:vault://secret/job`
- **PostgreSQL**: job-postgres:5433
- **Database credentials**: Managed by Vault
- **Service name**: `job` (chuẩn hóa từ `job-service`)

#### 2.5 CV Service (Port 9083) ⭐ NEW REDIS INTEGRATION
- **Vault integration**: `spring.config.import: optional:vault://secret/cv`
- **MongoDB**: cv-mongo:27018
- **Redis**: cv-redis:6381 (dedicated instance)
- **Service name**: `cv` (chuẩn hóa từ `cv-service`)

#### 2.6 Application Service (Port 9084)
- **Vault integration**: `spring.config.import: optional:vault://secret/application-service`
- **MongoDB**: application-mongo:27019

#### 2.7 Monitoring Service (Port 9086) ⭐ NEW SERVICE
- **Role**: Vault configuration management
- **Component**: VaultInitializer
- **Function**: Auto-populate Vault với service configurations

### 3. Monitoring & Observability

#### 3.1 Prometheus (Port 9090)
- **Metrics collection**: Từ tất cả Spring Boot services
- **Endpoint**: `/actuator/prometheus`
- **Scrape interval**: 15s

#### 3.2 Grafana (Port 3001)
- **Dashboard**: Spring Boot metrics
- **Data source**: Prometheus
- **Auto-provisioning**: Dashboard templates

#### 3.3 Kafka Ecosystem
- **Zookeeper**: Port 2181
- **Kafka**: Ports 9092, 29092
- **Kafka UI**: Port 8080

## 🔧 Hướng dẫn Setup môi trường mới

### Bước 1: Prerequisites
```bash
# Cài đặt Docker & Docker Compose
docker --version
docker-compose --version

# Clone repository
git clone <repository-url>
cd workfitai-platform
git checkout feature/user-service
```

### Bước 2: Environment Setup
```bash
# Tạo network (nếu chưa có)
docker network create workfitai-network

# Build tất cả services
docker-compose build

# Start infrastructure services trước
docker-compose up -d consul vault prometheus grafana zookeeper kafka kafka-ui

# Chờ 30 giây để infrastructure khởi tạo
sleep 30
```

### Bước 3: Start Database Services
```bash
# Start databases
docker-compose up -d auth-mongo cv-mongo application-mongo user-postgres job-postgres

# Start Redis instances  
docker-compose up -d auth-redis api-redis cv-redis

# Chờ databases ready
sleep 20
```

### Bước 4: Start Application Services
```bash
# Start monitoring service TRƯỚC (quan trọng cho Vault initialization)
docker-compose up -d monitoring-service

# Chờ Vault được populated (check logs)
docker-compose logs monitoring-service | grep "Vault secrets initialization completed"

# Start remaining services
docker-compose up -d auth-service user-service cv-service job-service application-service api-gateway
```

### Bước 5: Verification
```bash
# Check tất cả services
docker-compose ps

# Verify health endpoints
curl http://localhost:9085/actuator/health  # API Gateway
curl http://localhost:9080/actuator/health  # Auth Service  
curl http://localhost:9081/actuator/health  # User Service
curl http://localhost:9082/actuator/health  # Job Service
curl http://localhost:9083/actuator/health  # CV Service
curl http://localhost:9084/actuator/health  # Application Service
curl http://localhost:9086/actuator/health  # Monitoring Service
```

## 🔐 Vault Management

### Truy cập Vault UI
```bash
# Vault UI: http://localhost:8200
# Token: dev-token
```

### Vault CLI Commands
```bash
# Set Vault address
export VAULT_ADDR=http://localhost:8200
export VAULT_TOKEN=dev-token

# List secrets
vault kv list secret/

# Get specific service config
vault kv get secret/auth
vault kv get secret/user
vault kv get secret/job
vault kv get secret/cv
vault kv get secret/application-service
```

### Service Configuration Structure
```bash
# Auth Service (MongoDB + Redis)
vault kv get secret/auth
# Returns:
# spring.data.mongodb.host=auth-mongo
# spring.data.mongodb.port=27017
# spring.data.redis.host=auth-redis
# spring.data.redis.port=6379

# User Service (PostgreSQL)
vault kv get secret/user  
# Returns:
# spring.datasource.url=jdbc:postgresql://user-postgres:5432/user_db
# spring.datasource.username=user
# spring.datasource.password=user@123

# Job Service (PostgreSQL)
vault kv get secret/job
# Returns:
# spring.datasource.url=jdbc:postgresql://job-postgres:5432/job_db
# spring.datasource.username=user
# spring.datasource.password=job@123

# CV Service (MongoDB + Redis)
vault kv get secret/cv
# Returns:
# spring.data.mongodb.host=cv-mongo
# spring.data.mongodb.port=27018
# spring.data.redis.host=cv-redis
# spring.data.redis.port=6379
```

### Manual Vault Configuration (nếu cần)
```bash
# Nếu VaultInitializer không chạy, có thể config thủ công:

# Auth service
vault kv put secret/auth \
  spring.data.mongodb.host=auth-mongo \
  spring.data.mongodb.port=27017 \
  spring.data.redis.host=auth-redis \
  spring.data.redis.port=6379

# User service  
vault kv put secret/user \
  spring.datasource.url=jdbc:postgresql://user-postgres:5432/user_db \
  spring.datasource.username=user \
  spring.datasource.password=user@123

# Job service
vault kv put secret/job \
  spring.datasource.url=jdbc:postgresql://job-postgres:5432/job_db \
  spring.datasource.username=user \
  spring.datasource.password=job@123

# CV service
vault kv put secret/cv \
  spring.data.mongodb.host=cv-mongo \
  spring.data.mongodb.port=27018 \
  spring.data.redis.host=cv-redis \
  spring.data.redis.port=6379
```

## 🔗 Service Integration via API Gateway

### Service Routing
API Gateway sử dụng Consul service discovery để auto-route requests:

```bash
# Pattern: http://localhost:9085/{service-name}/{original-path}

# Auth endpoints
curl http://localhost:9085/auth/api/auth/login
curl http://localhost:9085/auth/api/auth/register

# User endpoints  
curl http://localhost:9085/user/api/users/profile
curl http://localhost:9085/user/api/users/list

# Job endpoints
curl http://localhost:9085/job/api/jobs/search
curl http://localhost:9085/job/api/jobs/create

# CV endpoints
curl http://localhost:9085/cv/api/cv/upload
curl http://localhost:9085/cv/api/cv/analyze

# Application endpoints
curl http://localhost:9085/application-service/api/applications/submit
```

### Load Balancer Configuration
```yaml
# API Gateway sử dụng Spring Cloud LoadBalancer
spring:
  cloud:
    loadbalancer:
      ribbon:
        enabled: false # Sử dụng Spring Cloud LoadBalancer thay vì Ribbon
```

## 📊 Monitoring & Observability

### Consul Service Discovery
```bash
# Consul UI: http://localhost:8500
# Xem tất cả registered services và health status
```

### Prometheus Metrics
```bash  
# Prometheus UI: http://localhost:9090
# Query examples:
# - up{job="spring-actuator"} # Service availability
# - jvm_memory_used_bytes # Memory usage
# - http_server_requests_seconds_count # Request count
```

### Grafana Dashboards
```bash
# Grafana UI: http://localhost:3001
# Default login: admin/admin
# Pre-configured dashboards:
# - Spring Boot 2.1 Statistics
# - JVM metrics
# - HTTP request metrics
```

### Application Logs
```bash
# Xem logs của specific service
docker-compose logs -f auth-service
docker-compose logs -f user-service  
docker-compose logs -f job-service
docker-compose logs -f cv-service
docker-compose logs -f api-gateway
docker-compose logs -f monitoring-service

# Xem logs tất cả services
docker-compose logs -f
```

### Health Checks
```bash
# Actuator health endpoints
curl http://localhost:9080/actuator/health  # Auth Service
curl http://localhost:9081/actuator/health  # User Service  
curl http://localhost:9082/actuator/health  # Job Service
curl http://localhost:9083/actuator/health  # CV Service
curl http://localhost:9084/actuator/health  # Application Service
curl http://localhost:9085/actuator/health  # API Gateway
curl http://localhost:9086/actuator/health  # Monitoring Service

# Detailed health info
curl http://localhost:9080/actuator/health/vault    # Vault connectivity
curl http://localhost:9080/actuator/health/mongo    # MongoDB connectivity  
curl http://localhost:9080/actuator/health/redis    # Redis connectivity
curl http://localhost:9081/actuator/health/db       # PostgreSQL connectivity
```

## 🐛 Troubleshooting

### Common Issues

#### 1. Vault Connection Issues
```bash
# Check Vault status
docker-compose logs vault

# Verify Vault is accessible
curl http://localhost:8200/v1/sys/health

# Check service Vault configuration
docker-compose logs monitoring-service | grep "Vault"
```

#### 2. Service Discovery Issues  
```bash
# Check Consul
curl http://localhost:8500/v1/catalog/services

# Verify service registration
curl http://localhost:8500/v1/catalog/service/auth
curl http://localhost:8500/v1/catalog/service/user
```

#### 3. Database Connection Issues
```bash
# Check database containers
docker-compose logs user-postgres
docker-compose logs job-postgres  
docker-compose logs auth-mongo
docker-compose logs cv-mongo

# Test database connections
docker-compose exec user-postgres psql -U user -d user_db -c "\l"
docker-compose exec job-postgres psql -U user -d job_db -c "\l"  
```

#### 4. Redis Connection Issues
```bash
# Check Redis instances
docker-compose logs auth-redis
docker-compose logs cv-redis
docker-compose logs api-redis

# Test Redis connections  
docker-compose exec auth-redis redis-cli ping
docker-compose exec cv-redis redis-cli ping
```

#### 5. Service Startup Order Issues
```bash
# Recommended startup sequence:
# 1. Infrastructure (Consul, Vault, Prometheus, Grafana)
# 2. Databases (PostgreSQL, MongoDB, Redis)  
# 3. Monitoring Service (for Vault initialization)
# 4. Application Services
# 5. API Gateway (last)

# Force recreate services
docker-compose down
docker-compose up --build
```

### Debug Commands
```bash
# Service status
docker-compose ps

# Resource usage
docker stats

# Network inspection
docker network inspect workfitai-network

# Container inspection  
docker inspect <container-name>

# Log streaming
docker-compose logs -f --tail=100 <service-name>
```

## 📝 Environment Variables

### Required Environment Variables
Không có environment variables bắt buộc - tất cả được quản lý qua Vault.

### Optional Environment Variables  
```bash
# For development override
export VAULT_ADDR=http://localhost:8200
export VAULT_TOKEN=dev-token
export CONSUL_HOST=localhost:8500
```

## 🔄 Development Workflow

### Code Changes
```bash
# Rebuild specific service sau khi thay đổi code
docker-compose build <service-name>
docker-compose up -d <service-name>

# Rebuild tất cả
docker-compose build
docker-compose up -d
```

### Configuration Changes
```bash
# Nếu thay đổi Vault config, restart monitoring service
docker-compose restart monitoring-service

# Sau đó restart services sử dụng config đó
docker-compose restart auth-service user-service job-service cv-service
```

### Database Schema Changes
```bash
# PostgreSQL migrations sẽ auto-run khi service start
# MongoDB changes sẽ auto-apply qua Spring Data

# Manual database access
docker-compose exec user-postgres psql -U user -d user_db
docker-compose exec auth-mongo mongosh
```

## 📚 References

- [Spring Cloud Gateway Documentation](https://spring.io/projects/spring-cloud-gateway)
- [HashiCorp Vault Documentation](https://developer.hashicorp.com/vault/docs)
- [Spring Cloud Vault Documentation](https://spring.io/projects/spring-cloud-vault)
- [Consul Service Discovery](https://www.consul.io/docs/discovery)
- [Prometheus Spring Boot](https://docs.spring.io/spring-boot/docs/current/actuator-api/htmlsingle/)

---

**Lưu ý**: File này được tạo tự động dựa trên cấu hình hiện tại. Cập nhật khi có thay đổi infrastructure.