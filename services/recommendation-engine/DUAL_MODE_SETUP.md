# Recommendation Engine - Dual Mode Setup

## 📦 Modes

### 1. Docker Mode (Production/Host)
Chạy trong Docker, kết nối với các services qua **service names**:
- `vault:8200`
- `kafka:29092`
- `consul:8500`

### 2. Local Mode (Development/Testing)
Chạy trực tiếp trên Mac, kết nối với Docker services qua **localhost**:
- `localhost:8200`
- `localhost:9092`
- `localhost:8500`

---

## 🚀 Quick Start

### Docker Mode (Recommended for Production)

```bash
# Start everything (infrastructure + services)
./dev.sh full up

# Or start only recommendation-engine
docker-compose --profile services up recommendation-engine -d

# View logs
docker logs -f recommendation-engine

# Health check
curl http://localhost:8001/health
```

### Local Mode (Recommended for Development)

**Step 1: Start Infrastructure Services**
```bash
# Start only infrastructure (Vault, Kafka, MongoDB, PostgreSQL, etc.)
./dev.sh infra up
```

**Step 2: Run Recommendation Engine Locally**
```bash
cd services/recommendation-engine

# Run the script (auto-installs dependencies, checks services)
./run-local.sh
```

The script will:
- ✅ Create Python virtual environment if needed
- ✅ Install dependencies from `requirements.txt`
- ✅ Check if Docker services (Vault, Kafka) are running
- ✅ Load `.env.local` configuration
- ✅ Start server with hot-reload on port 8000

**Access:**
- API: http://localhost:8000
- Health: http://localhost:8000/health
- Docs: http://localhost:8000/docs

---

## ⚙️ Configuration Files

### `.env.local` (Local Mode)
Located at `services/recommendation-engine/.env.local`

Key differences from Docker:
```bash
# Vault: Docker service → localhost
VAULT_ADDR=http://localhost:8200

# Kafka: Internal port → external port
KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# Model path: Container path → local path
MODEL_PATH=./models/bi-encoder-e5-large
FAISS_INDEX_PATH=./data/faiss_index
```

### `docker-compose.yml` (Docker Mode)
Recommendation engine service config:
```yaml
environment:
  - VAULT_ADDR=http://vault:8200
  - KAFKA_BOOTSTRAP_SERVERS=kafka:29092
  - MODEL_PATH=/app/models/bi-encoder-e5-large
```

---

## 🔄 Switching Between Modes

### From Docker → Local
```bash
# Stop Docker recommendation-engine
docker stop recommendation-engine

# Run locally
cd services/recommendation-engine
./run-local.sh
```

### From Local → Docker
```bash
# Stop local process (Ctrl+C)

# Start Docker container
docker-compose --profile services up recommendation-engine -d
```

---

## 🧪 Testing Both Modes

### Test Docker Mode
```bash
# Ensure running in Docker
docker ps | grep recommendation-engine

# Test endpoint
curl http://localhost:8001/health
```

### Test Local Mode
```bash
# Ensure running locally (check terminal with ./run-local.sh)

# Test endpoint
curl http://localhost:8000/health
```

### Test Job Service Integration
```bash
# Job Service connects to recommendation-engine
# Check application.yml: service.recommendation.url

# Docker mode: http://recommendation-engine:8000
# Local mode: http://localhost:8000 or http://localhost:8001
```

---

## 📊 Port Mapping

| Mode | Port | URL | Notes |
|------|------|-----|-------|
| Docker | 8001 | http://localhost:8001 | Mapped from container :8000 |
| Local | 8000 | http://localhost:8000 | Direct on host |

**Tip:** Job Service can be configured to use either:
```yaml
# In job-service application.yml
service:
  recommendation:
    url: ${RECOMMENDATION_ENGINE_URL:http://localhost:8001}  # Docker
    # url: ${RECOMMENDATION_ENGINE_URL:http://localhost:8000}  # Local
```

---

## 🛠️ Troubleshooting

### Local Mode: "Connection Refused"
```bash
# Check if infrastructure is running
docker ps | grep -E "vault|kafka|consul"

# Start infrastructure
./dev.sh infra up

# Verify connectivity
curl http://localhost:8200/v1/sys/health  # Vault
```

### Local Mode: Model Not Found
```bash
# Check model directory
ls -la services/recommendation-engine/models/bi-encoder-e5-large

# Model will auto-download on first run if missing
# Or manually place model files in models/ directory
```

### Docker Mode: Out of Memory
```bash
# Increase Docker memory allocation in Docker Desktop
# Settings → Resources → Memory → 8GB+

# Or run in Local mode to avoid Docker limits
./run-local.sh
```

### Port Conflict
```bash
# Local mode using 8000, Docker using 8001
# If port 8000 is busy, edit .env.local:
PORT=8002

# If Docker port 8001 is busy, edit docker-compose.yml:
ports:
  - "8003:8000"  # Change 8001 → 8003
```

---

## 🎯 Best Practices

### Use Docker Mode When:
- ✅ Running full system integration tests
- ✅ Deploying to production/staging
- ✅ Other developers need consistent environment
- ✅ CI/CD pipelines

### Use Local Mode When:
- ✅ Developing ML features (model training, tuning)
- ✅ Debugging Python code with breakpoints
- ✅ Testing with large models (avoid Docker memory limits)
- ✅ Fast iteration with hot-reload
- ✅ Need direct access to model files

---

## 📝 Development Workflow Example

```bash
# 1. Start infrastructure
./dev.sh infra up

# 2. Run recommendation-engine locally for development
cd services/recommendation-engine
./run-local.sh

# 3. Run job-service in Docker (or locally via IDE)
docker-compose up job-service -d

# 4. Make changes to Python code
# → Uvicorn auto-reloads on file changes

# 5. Test integration
curl -X POST http://localhost:9082/public/recommendations/for-me \
  -H "Authorization: Bearer YOUR_TOKEN"

# 6. When done, stop local and start Docker
# Ctrl+C to stop local
docker-compose up recommendation-engine -d
```

---

## 🔗 Related Services Integration

### Job Service Configuration
Update `services/job-service/src/main/resources/application.yml`:

```yaml
service:
  recommendation:
    # Docker mode (recommendation-engine in Docker)
    url: ${RECOMMENDATION_ENGINE_URL:http://recommendation-engine:8000}
    
    # Local mode (recommendation-engine on host)
    # url: ${RECOMMENDATION_ENGINE_URL:http://host.docker.internal:8000}
```

**Note:** Use `host.docker.internal` to access host from Docker container

### Environment Variable Override
```bash
# When running job-service
export RECOMMENDATION_ENGINE_URL=http://localhost:8000  # For local
docker-compose up job-service
```

---

## ✅ Verification Checklist

- [ ] Infrastructure services running: `docker ps`
- [ ] Vault accessible: `curl http://localhost:8200/v1/sys/health`
- [ ] Kafka accessible: `docker logs kafka-workfitai | tail`
- [ ] Model files exist: `ls services/recommendation-engine/models/`
- [ ] Python venv activated: `which python` shows `venv/bin/python`
- [ ] Dependencies installed: `pip list | grep sentence-transformers`
- [ ] Server running: `curl http://localhost:8000/health`
- [ ] FAISS index created: `ls services/recommendation-engine/data/faiss_index/`
