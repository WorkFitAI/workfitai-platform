# WorkFitAI Platform

## 📋 Overview

WorkFitAI is an AI-powered recruitment and job matching platform built on a modern microservices architecture using Spring Boot and Spring Cloud. The platform connects job seekers with employers through intelligent matching, streamlined application workflows, and comprehensive candidate management.

**Technology Stack**: Java 17 | Spring Boot 3.5.4 | Spring Cloud 2025.0.0 | PostgreSQL | MongoDB | Redis | Kafka | Consul | MinIO

---

## 📚 Documentation

- **[Project Overview & PDR](docs/project-overview-pdr.md)** - Product vision, features, and requirements
- **[System Architecture](docs/system-architecture.md)** - Microservices design, data flows, and infrastructure
- **[Codebase Summary](docs/codebase-summary.md)** - Service structure and API endpoints
- **[Code Standards](docs/code-standards.md)** - Java/Spring Boot coding conventions and best practices
- **[Application Flow](docs/APPLICATION_FLOW.md)** - Job application submission workflow with Saga pattern and Kafka events
- **[Registration Flow](docs/REGISTRATION_FLOW.md)** - User registration and approval workflow
- **[Development Guide](docs/DEVELOPMENT.md)** - Setup, configuration, and troubleshooting

---

## 🏗️ Architecture

### Microservices
- **auth-service** (9080) - Authentication & JWT token management
- **user-service** (9081) - User profile management
- **job-service** (9082) - Job posting & company management
- **application-service** (9084) - Job applications with Saga pattern
- **cv-service** (9083) - CV storage & management
- **api-gateway** (9085) - Routing & load balancing
- **monitoring-service** (9086) - Configuration & observability
- **notification-service** (Kafka consumer) - Email notifications via SMTP

### Infrastructure
- **Consul** (8500) - Service discovery
- **Kafka** (9092) - Event streaming
- **Vault** (8200) - Secrets management
- **MinIO** (9000) - Object storage for CVs
- **Prometheus** (9090) + **Grafana** (3001) - Monitoring

---

## 🚀 Quick Start

### 1. Infrastructure Setup
```bash
# Start infrastructure services (databases, vault, kafka, etc.)
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
| PostgreSQL | 5432 | localhost:5432 | user: `workfit` / pass: `workfit123` |
| MongoDB | 27017 | localhost:27017 | user: `workfit` / pass: `workfit123` |
| Redis Master | 6379 | localhost:6379 | - |
| Prometheus | 9090 | http://localhost:9090 | - |
| Grafana | 3001 | http://localhost:3001 | - |

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

- Each service has a single `application.yml` with `docker` and `local` profiles
- Docker profile is default (for containers)
- Local profile for IDE development (set `SPRING_PROFILES_ACTIVE=local`)
- All configs are centrally managed in Vault
- Monitoring service auto-syncs configs to Vault on startup

## 📚 Useful Links

- **Vault UI**: http://localhost:8200 (token: `dev-token`)
- **Kafka UI**: http://localhost:8080
- **Consul UI**: http://localhost:8500
- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3001
- **MinIO Console**: http://localhost:9001 (user: `minioadmin` / pass: `minioadmin`)

---

## 🎯 Key Features

- **Hexagonal Architecture** in application-service for clean separation of concerns
- **Saga Pattern** for distributed transactions in job application workflow
- **Draft Application Workflow** - Save and submit applications in multiple steps (NEW)
- **Role-Based APIs** - 30 endpoints covering Candidate, HR, HR Manager, and Admin roles (NEW)
- **Advanced Application Management** - Status history, notes, bulk operations, analytics (NEW)
- **Event-Driven** communication via Kafka for decoupled microservices
- **JWT Authentication** with RSA-2048 signatures for secure inter-service communication
- **Service Discovery** with Consul for dynamic service registration
- **Secrets Management** with Vault for centralized configuration
- **CV File Storage** with MinIO (S3-compatible object storage)
- **Monitoring & Observability** with Prometheus and Grafana

---

## 🛠️ Tech Highlights

- **Spring Boot 3.5.4** - Latest enterprise Java framework
- **Spring Cloud 2025.0.0** - Cloud-native patterns
- **Hexagonal Architecture** - Clean architecture in application-service
- **MapStruct** - Type-safe DTO mapping
- **OpenAPI/Swagger** - API documentation
- **Testcontainers** - Integration testing (planned)

---

## 📖 Learn More

Visit the [docs](docs/) directory for comprehensive documentation:
- Architecture diagrams and data flows
- API endpoint specifications
- Coding standards and conventions
- Development workflows and best practices

---

## 🤝 Contributing

1. Follow the [Code Standards](docs/code-standards.md)
2. Use [Conventional Commits](docs/code-standards.md#git--commit-standards)
3. Write tests for new features (>80% coverage)
4. Update documentation for significant changes

---

**Version**: 0.0.1-SNAPSHOT
**License**: Proprietary
**Last Updated**: 2025-12-11