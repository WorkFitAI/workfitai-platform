# =============================================================================
# WorkFitAI Platform - Development Guide
# =============================================================================

## 🚀 Quick Start

### Development Script (Recommended)
```bash
# Start everything with auto-build
./dev.sh full up

# Start only infrastructure
./dev.sh infra up

# Restart with rebuild
./dev.sh full restart

# Stop everything
./dev.sh full stop

# View logs
./dev.sh full logs
./dev.sh full logs monitoring-service

# Clean everything
./dev.sh full clean
```

### Manual Docker Compose Commands
```bash
# Always rebuild and start
docker-compose --profile full up --build -d

# Start only infrastructure
docker-compose --profile infra up --build -d

# Stop everything
docker-compose --profile full down

# Force rebuild without cache
docker-compose --profile full build --no-cache
docker-compose --profile full up -d
```

## 📊 Service Endpoints

### Infrastructure Services
- **Consul UI**: http://localhost:8500
- **Vault UI**: http://localhost:8200 (Token: `dev-token`)
- **Kafka UI**: http://localhost:8080
- **Grafana**: http://localhost:3001 (admin/admin)
- **Prometheus**: http://localhost:9090

### Application Services
- **Auth Service**: http://localhost:9080
- **User Service**: http://localhost:9081
- **Job Service**: http://localhost:9082
- **CV Service**: http://localhost:9083
- **Application Service**: http://localhost:9084
- **API Gateway**: http://localhost:9085
- **Monitoring Service**: http://localhost:9086

## 🔧 Vault Management

### Check Vault Status
```bash
curl http://localhost:9086/api/vault/status
```

### Reinitialize Vault Secrets
```bash
curl -X POST http://localhost:9086/api/vault/reinitialize
```

### View Service Configuration
```bash
curl http://localhost:9086/api/vault/config/auth-service
```

## 🐛 Troubleshooting

### View Service Logs
```bash
# All services
./dev.sh full logs

# Specific service
./dev.sh full logs monitoring-service
docker-compose logs -f monitoring-service
```

### Rebuild Specific Service
```bash
docker-compose build monitoring-service --no-cache
docker-compose up monitoring-service -d
```

### Clean Start
```bash
./dev.sh full clean
./dev.sh full up
```

## 📁 Project Structure

```
workfitai-platform/
├── services/
│   ├── auth-service/
│   ├── user-service/
│   ├── job-service/
│   ├── cv-service/
│   ├── application-service/
│   ├── api-gateway/
│   └── monitoring-service/    # Vault initialization & monitoring
├── vault/                     # Vault configuration
├── grafana/                   # Grafana dashboards
├── initialize/                # Initialization scripts
├── docker-compose.yml         # Main composition
└── dev.sh                     # Development helper script
```

## 🔑 Environment Variables

Key variables in `.env.local`:
- `VAULT_TOKEN=dev-token`
- `SPRING_PROFILES_ACTIVE=local`
- Database connection strings
- Service URLs

## 🏗️ Development Workflow

1. **Make code changes**
2. **Restart with rebuild**: `./dev.sh full restart`
3. **Check logs**: `./dev.sh full logs [service-name]`
4. **Access services** via endpoints above

## ⚠️ Important Notes

- Services will auto-rebuild when using `./dev.sh` or `--build` flag
- Vault secrets are automatically initialized by monitoring-service
- Use `dev-token` to access Vault UI
- All services use `docker` Spring profile in containers