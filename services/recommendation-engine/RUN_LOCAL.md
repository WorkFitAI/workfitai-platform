# Running Recommendation Engine Locally

## Why Local Mode?

The E5-Large Sentence Transformer model (1.3GB) is too heavy for Docker container memory limits, causing segmentation faults. Running locally avoids these constraints.

## Quick Start

```bash
cd services/recommendation-engine
./run-local.sh
```

This script will:
1. ✅ Create Python virtual environment (if needed)
2. ✅ Install all dependencies from `requirements.txt`
3. ✅ Pre-download Sentence Transformer model
4. ✅ Start FastAPI server on http://localhost:8001

## Environment Configuration

The script automatically sets:
- **Vault**: Connects to Docker at `http://localhost:8200`
- **Kafka**: Connects to Docker at `localhost:9092`
- **Model**: Uses `all-MiniLM-L6-v2` (384-dim, 80MB)
- **Port**: 8001 (to avoid conflict with other services)
- **Data**: Stores FAISS index in `./data/faiss_index`

## Testing

Once started, test the service:

```bash
# Health check
curl http://localhost:8001/health | jq

# Expected output:
{
  "status": "healthy",
  "service": "recommendation-engine",
  "version": "1.0.0",
  "environment": "local",
  "components": {
    "model": {
      "loaded": true,
      "path": "sentence-transformers/all-MiniLM-L6-v2"
    },
    "faissIndex": {
      "loaded": true,
      "totalJobs": 0,
      "dimension": 384
    },
    "kafkaConsumer": {
      "enabled": true,
      "connected": true,
      "topics": ["job.created", "job.updated", "job.deleted"]
    }
  }
}
```

## Create a Job to Test Kafka Integration

```bash
# 1. Login as HR in Postman
POST http://localhost:9085/auth/login

# 2. Create a job
POST http://localhost:9085/job/hr/jobs
# (Use Job-Service Postman collection)

# 3. Check recommendation engine logs
# You should see:
# - "Received event: JOB_CREATED"
# - "Processing JOB_CREATED for jobId: xxx"
# - "Formatted job text length: xxx"
# - "Generated embedding shape: (384,)"
# - "✓ Added job xxx to FAISS index"

# 4. Verify in health check
curl http://localhost:8001/health | jq '.components.faissIndex.totalJobs'
# Should return: 1
```

## Switching Models

### Use Smaller Model (Faster, Less Accurate)
```bash
export MODEL_PATH=sentence-transformers/all-MiniLM-L6-v2
export MODEL_DIMENSION=384
```

### Use Larger Model (Slower, More Accurate)
```bash
export MODEL_PATH=sentence-transformers/all-mpnet-base-v2
export MODEL_DIMENSION=768
```

### Use E5-Large (Production Quality)
```bash
export MODEL_PATH=intfloat/e5-large
export MODEL_DIMENSION=1024
```

Then restart: `./run-local.sh`

## Stopping the Service

Press `Ctrl+C` in the terminal running the service.

## Troubleshooting

### Port Already in Use
```bash
# Kill process on port 8001
lsof -ti:8001 | xargs kill -9

# Or change port
export PORT=8002
./run-local.sh
```

### Kafka Connection Failed
```bash
# Verify Kafka is running in Docker
docker ps | grep kafka

# Check Kafka is listening on localhost:9092
nc -zv localhost 9092
```

### Model Download Slow
Models are downloaded from HuggingFace. First time will take 1-5 minutes depending on model size:
- MiniLM: ~80MB (~30 seconds)
- MPNet: ~400MB (~2 minutes)
- E5-Large: ~1.3GB (~5 minutes)

Subsequent runs will use cached model from `~/.cache/huggingface/`.

### Memory Issues
If your system has <8GB RAM, use smaller model:
```bash
export MODEL_PATH=sentence-transformers/all-MiniLM-L6-v2
```

## Development Workflow

### With Auto-Reload
The script runs with `--reload` flag, so code changes automatically restart the server.

### View Logs
Logs are printed to stdout. To save to file:
```bash
./run-local.sh 2>&1 | tee logs/app.log
```

### Run Tests
```bash
source venv/bin/activate
pytest tests/
```

## Production Deployment

For production, use larger models and proper infrastructure:
1. **E5-Large** model for best accuracy
2. **GPU instance** for faster inference
3. **Redis cache** for popular searches
4. **Load balancer** for multiple instances
5. **Kubernetes** for auto-scaling

## Switching Back to Docker

When ready to run in Docker again:

1. Stop local service (Ctrl+C)
2. Update Vault with correct model path:
```bash
VAULT_ADDR=http://localhost:8200 VAULT_TOKEN=dev-token \
vault kv patch secret/recommendation-engine \
  model.path="sentence-transformers/all-MiniLM-L6-v2"
```
3. Start Docker container:
```bash
docker-compose up -d recommendation-engine
```

## File Structure

```
services/recommendation-engine/
├── run-local.sh           # ← Run this script
├── venv/                  # Virtual environment (auto-created)
├── data/                  # FAISS index storage (auto-created)
├── logs/                  # Application logs (auto-created)
├── app/
│   ├── main.py
│   ├── api/
│   ├── services/
│   └── kafka_consumer/
├── requirements.txt
└── README.md
```
