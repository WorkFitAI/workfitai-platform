# WorkFitAI Platform

## 📋 Overview

WorkFitAI is an AI-powered recruitment and job matching platform built on modern microservices architecture using Spring Boot and Spring Cloud. Platform connects job seekers with employers through intelligent matching, streamlined application workflows, and comprehensive candidate management.

**Technology Stack**: Java 17 | Spring Boot 3.5.4 | Spring Cloud 2025.0.0 | PostgreSQL | MongoDB | Redis | Kafka | Consul | MinIO | Zipkin

---

## 🏗️ Architecture

### Microservices
- **api-gateway** (8088) - Routing, load balancing, circuit breaker, rate limiting, distributed tracing
- **auth-service** (8081) - Authentication, JWT tokens, OAuth2, 2FA, session management
- **user-service** (8082) - User profile management, CRUD operations
- **job-service** (8083) - Job posting, company & skill management, recommendations
- **application-service** (8085) - Job applications (Saga pattern, hexagonal architecture)
- **cv-service** (8084) - CV storage & management (MinIO)
- **monitoring-service** (9080) - Configuration & observability, log classification
- **notification-service** (Kafka + WebSocket) - Email notifications (SMTP) & real-time push
- **recommendation-engine** (In Development) - AI-powered job-candidate matching

### Infrastructure
- **Consul** (8500) - Service discovery
- **Kafka** (9092) - Event streaming
- **Vault** (8200) - Secrets management
- **MinIO** (9000) - Object storage for CVs
- **Prometheus** (9090) + **Grafana** (3001) - Monitoring
- **Zipkin** (9411) - Distributed tracing
- **Elasticsearch** (9200) + **Kibana** (5601) + **Fluent Bit** - Logging stack

---

## 🚀 Quick Start

### 1. Infrastructure Setup
```bash
# Start infrastructure services (databases, vault, kafka, zipkin, etc.)
docker-compose --profile infra up -d
```

### 2. Application Services

#### Option A: Run All Services in Docker
```bash
# Start all application services in containers
docker-compose --profile services up -d

# Or start everything at once
docker-compose --profile full up -d
```

#### Option B: Hybrid Development (Infrastructure + Local Services)
```bash
# 1. Start infrastructure
docker-compose --profile infra up -d

# 2. Run specific service locally in IDE
export SPRING_PROFILES_ACTIVE=local
export VAULT_TOKEN=dev-token

# Then run service in your IDE or:
cd services/user-service
./mvnw spring-boot:run
```

## 📋 Service Ports

| Service | Port | URL |
|---------|------|-----|
| API Gateway | 8088 | http://localhost:8088 |
| Auth Service | 8081 | http://localhost:8081 |
| User Service | 8082 | http://localhost:8082 |
| Job Service | 8083 | http://localhost:8083 |
| CV Service | 8084 | http://localhost:8084 |
| Application Service | 8085 | http://localhost:8085 |
| Monitoring Service | 9080 | http://localhost:9080 |

## 🔧 Infrastructure Services

| Service | Port | URL | Credentials |
|---------|------|-----|-------------|
| Vault | 8200 | http://localhost:8200 | Token: `dev-token` |
| Kafka UI | 8080 | http://localhost:8080 | - |
| Consul | 8500 | http://localhost:8500 | - |
| Zipkin | 9411 | http://localhost:9411 | - |
| Prometheus | 9090 | http://localhost:9090 | - |
| Grafana | 3001 | http://localhost:3001 | - |
| Kibana | 5601 | http://localhost:5601 | - |
| PostgreSQL | 5432 | localhost:5432 | user: `workfit` / pass: `workfit123` |
| MongoDB | 27017 | localhost:27017 | user: `workfit` / pass: `workfit123` |
| Redis Master | 6379 | localhost:6379 | - |
| MinIO Console | 9001 | http://localhost:9001 | user: `minioadmin` / pass: `minioadmin` |

## 🔄 Common Commands

### Stop Services
```bash
# Stop all
docker-compose down

# Stop specific profile
docker-compose --profile services down
```

### View Logs
```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f user-service
```

### Restart Service
```bash
# Restart specific service
docker-compose restart user-service
```

### Health Check
```bash
# Check all services health
curl http://localhost:9080/actuator/health

# Check specific service
curl http://localhost:8082/actuator/health
```

## 🏗️ Development Workflow

### Full Docker Development
```bash
docker-compose --profile full up -d
# All services running in containers
```

### Hybrid Development (Recommended)
```bash
# 1. Start infrastructure
docker-compose --profile infra up -d

# 2. Start some services in Docker
docker-compose --profile services up user-service job-service -d

# 3. Stop the service you want to develop locally
docker stop workfitai-platform-auth-service-1

# 4. Run locally in IDE with:
SPRING_PROFILES_ACTIVE=local
VAULT_TOKEN=dev-token
```

### Local-Only Development
```bash
# Start only infrastructure
docker-compose --profile infra up -d

# Set environment variables for all services
export SPRING_PROFILES_ACTIVE=local
export VAULT_TOKEN=dev-token

# Run each service in separate terminals or IDE
```

## 🐛 Troubleshooting

### Service won't start
1. Check if port is available: `lsof -i :8082`
2. Check Docker containers: `docker-compose ps`
3. Check logs: `docker-compose logs service-name`

### Database connection issues
1. Ensure infrastructure is running: `docker-compose --profile infra ps`
2. Check database logs: `docker-compose logs postgres`

### Vault connection issues
1. Verify Vault is running: `curl http://localhost:8200/v1/sys/health`
2. Check token: Should be `dev-token`
3. Verify configs in Vault: Browse to http://localhost:8200

### Configuration issues
1. Check if service configs are synced to Vault
2. Verify environment variables are set correctly
3. Check application.yml profiles

## 🔧 Configuration Notes

- Each service has `application.yml` with `docker` and `local` profiles
- Docker profile is default (for containers)
- Local profile for IDE development (set `SPRING_PROFILES_ACTIVE=local`)
- All configs centrally managed in Vault
- Monitoring service auto-syncs configs to Vault on startup

## 📚 Useful Links

- **Vault UI**: http://localhost:8200 (token: `dev-token`)
- **Kafka UI**: http://localhost:8080
- **Consul UI**: http://localhost:8500
- **Zipkin**: http://localhost:9411
- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3001
- **Kibana**: http://localhost:5601
- **MinIO Console**: http://localhost:9001 (user: `minioadmin` / pass: `minioadmin`)

---

## 🎯 Key Features

### Core Features (COMPLETE)
- **Hexagonal Architecture** in application-service for clean separation of concerns
- **Saga Pattern** for distributed transactions in job application workflow
- **Draft Application Workflow** - Save and submit applications in multiple steps
- **Role-Based APIs** - 39 endpoints covering Candidate, HR, HR Manager, and Admin roles
- **Advanced Application Management** - Status history, notes, bulk operations, analytics
- **Event-Driven** communication via Kafka for decoupled microservices
- **JWT Authentication** with RSA-2048 signatures for secure inter-service communication
- **OAuth2 Authentication** - Google and GitHub login integration
- **Two-Factor Authentication (2FA)** - Google Authenticator support
- **Session Management** - Geo-location tracking, device fingerprinting
- **Service Discovery** with Consul for dynamic service registration
- **Secrets Management** with Vault for centralized configuration
- **CV File Storage** with MinIO (S3-compatible object storage)
- **Monitoring & Observability** with Prometheus, Grafana, and Zipkin

### Recent Enhancements (Dec 2025)
- **Circuit Breaker** - Resilience4j with service-specific configurations (ignores 4xx, trips on 5xx)
- **Rate Limiting** - Redis-based rate limiting per user/IP
- **Distributed Tracing** - Zipkin integration with trace ID propagation across all services
- **WebSocket Notifications** - Real-time push notifications via WebSocket
- **Email Notifications** - Thymeleaf templates for application confirmations
- **Log Aggregation** - Fluent Bit → Elasticsearch → Kibana pipeline
- **Log Classification** - Automated log categorization and analysis

### Advanced Patterns
- **Circuit Breaker Pattern** - Fault tolerance with fallback responses
- **Retry Pattern** - Exponential backoff for transient failures
- **Saga Pattern** - Distributed transaction management with compensations
- **CQRS** - Separate read/write models for performance
- **Event Sourcing** - Audit trail via Kafka event logs
- **API Gateway Pattern** - Centralized routing with opaque tokens

---

## 🛠️ Tech Highlights

- **Spring Boot 3.5.4** - Latest enterprise Java framework
- **Spring Cloud 2025.0.0** - Cloud-native patterns
- **Hexagonal Architecture** - Clean architecture in application-service
- **Resilience4j** - Circuit breaker, rate limiting, retry
- **Micrometer Tracing** - Distributed tracing with Zipkin
- **MapStruct** - Type-safe DTO mapping
- **OpenAPI/Swagger** - API documentation
- **Testcontainers** - Integration testing
- **Fluent Bit** - Log aggregation
- **Elasticsearch** - Log storage and search

---

## 📊 Event-Driven Architecture

### Kafka Topics
- `user-registration-events` - User creation (auth → user)
- `company-creation-events` - Company creation (auth → job)
- `application-events` - Application submission (application → notification)
- `application-notification-events` - Dual email notifications (application → notification)
- `job-stats-update` - Application counter updates (application → job)
- `application-status` - Status change events
- `session-invalidation-events` - Session revocation
- `oauth-events` - OAuth login/link/unlink events
- `password-change-events` - Password reset/change events

### Event Flow Example: Application Submission
```
1. Candidate submits application → application-service
2. Saga Orchestrator validates → job-service (Feign)
3. CV uploaded → MinIO
4. Application saved → MongoDB
5. Events published → Kafka:
   - ApplicationCreatedEvent → application-events
   - JobStatsUpdateEvent → job-stats-update
   - NotificationEvent → application-notification-events
6. notification-service consumes events
7. Emails sent (candidate + HR)
8. WebSocket push notification
9. job-service updates totalApplications counter
```

---

## 🔒 Security

### Authentication Methods
1. **Username/Password** - Traditional login with OTP verification
2. **OAuth2** - Google and GitHub login
3. **Two-Factor Authentication (2FA)** - Google Authenticator

### Security Features
- RSA-2048 JWT signatures
- Opaque token system (JWT hidden from client)
- Refresh token rotation
- Session management with geo-location
- Role-based access control (RBAC)
- Input validation (Jakarta Validation)
- SQL injection prevention (JPA parameterized queries)
- XSS prevention (response encoding)
- CSRF protection
- Security headers (CSP, HSTS, X-Frame-Options)
- Rate limiting (Redis-based)
- Secrets management (Vault)

---

**Version**: 0.0.1-SNAPSHOT
**License**: Proprietary
**Last Updated**: 2025-12-27
