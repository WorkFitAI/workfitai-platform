# WorkFitAI Recommendation Engine

Semantic job recommendation service using Sentence Transformers and FAISS vector similarity search.

## Features

- 🤖 **Semantic Search**: Natural language job search using sentence embeddings
- 📄 **Resume Matching**: Upload PDF resume and get personalized job recommendations
- 👤 **Profile Matching**: Match user profiles with suitable job listings
- 🔍 **Similar Jobs**: Find jobs similar to a reference job
- ⚡ **Real-time Updates**: Kafka consumer for immediate job indexing
- 🔄 **Initial Sync**: Batch sync from Job Service API
- 🎯 **Filters**: Location, salary, experience level, employment type

## Architecture

### Components

- **Embedding Generator**: Sentence Transformer model (all-MiniLM-L6-v2, 384-dim)
- **FAISS Index**: IndexFlatIP for cosine similarity search
- **Resume Parser**: PDF parsing with PyPDF2 and pdfplumber
- **Kafka Consumer**: Real-time job event processing
- **Job Sync**: Pagination-based batch sync from Job Service

### Technology Stack

- **Framework**: FastAPI 0.104.1
- **ML Model**: sentence-transformers/all-MiniLM-L6-v2 (80MB, 384-dim)
- **Vector Search**: FAISS-CPU 1.7.4
- **Message Queue**: kafka-python 2.0.2
- **Config Management**: HashiCorp Vault
- **PDF Processing**: PyPDF2, pdfplumber

## Quick Start

### Local Development (Recommended)

**Why Local?** The ML model (80MB) runs better outside Docker with native Python resources and MPS acceleration on Apple Silicon.

```bash
# Start full platform with recommendation engine
./dev.sh full up

# Or start only recommendation engine
docker-compose --profile services up recommendation-engine

# View logs
./dev.sh full logs recommendation-engine
```


```bash
cd services/recommendation-engine

# Start service (auto-creates venv, installs deps, downloads model)
./run-local.sh

# Service will be available at http://localhost:8001
# API docs: http://localhost:8001/docs
```

**Prerequisites:**
- Python 3.10+
- Docker services running (Vault, Kafka, Job Service)
- Port 8001 available

### Docker Deployment

**Note:** Not recommended for development due to model memory constraints. For production, use dedicated GPU server or cloud ML services.

```bash
# Build image
docker build -t recommendation-engine:latest .

# Run container
docker run -d \
  --name recommendation-engine \
  -p 8001:8000 \
  -e VAULT_ADDR=http://vault:8200 \
  -e VAULT_TOKEN=your-token \
  -e KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
  recommendation-engine:latest
```

## API Endpoints

### Recommendation Endpoints

#### 1. Semantic Search
```bash
POST /api/v1/recommendations/search
Content-Type: application/json

{
  "query": "Senior Java Spring Boot developer",
  "topK": 10,
  "filters": {
    "locations": ["Ha Noi City"],
    "experienceLevels": ["SENIOR"],
    "minSalary": 1500,
    "maxSalary": 3000
  }
}
```

#### 4. Similar Jobs
```bash
POST /api/v1/recommendations/similar-jobs
Content-Type: application/json

{
  "jobId": "31c3f83a-0b89-4bac-bf31-954bef0c480a",
  "topK": 10,
  "excludeSameCompany": true
}
```

### Admin Endpoints

#### Trigger Manual Sync
```bash
POST /api/v1/recommendations/admin/sync
```

### Health Check
```bash
GET /health
```

Response:
```json
{
  "status": "healthy",
  "service": "recommendation-engine",
  "version": "1.0.0",
  "components": {
    "model": {
      "loaded": true,
      "path": "sentence-transformers/all-MiniLM-L6-v2"
    },
    "faissIndex": {
      "loaded": true,
      "totalJobs": 33,
      "dimension": 384
    },
    "kafkaConsumer": {
      "enabled": true,
      "connected": true
    }
  }
}
```

## Configuration

All configuration is managed via **HashiCorp Vault** at path `secret/recommendation-engine`.

### Key Configuration

| Config | Default | Description |
|--------|---------|-------------|
| `MODEL_PATH` | `sentence-transformers/all-MiniLM-L6-v2` | Sentence Transformer model |
| `MODEL_DIMENSION` | `384` | Embedding dimension |
| `FAISS_INDEX_PATH` | `./data/faiss_index` | FAISS index persistence path |
| `ENABLE_INDEX_PERSISTENCE` | `true` | Save/load index on startup/shutdown |
| `ENABLE_INITIAL_SYNC` | `true` | Run sync from Job Service on startup |
| `ENABLE_KAFKA_CONSUMER` | `true` | Enable real-time Kafka consumption |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka brokers |
| `KAFKA_CONSUMER_GROUP` | `recommendation-engine` | Consumer group ID |
| `JOB_SERVICE_URL` | `http://localhost:9085` | Job Service API Gateway URL |

### Environment Variables (Local)

Set in `run-local.sh`:
```bash
export VAULT_ADDR=http://localhost:8200
export VAULT_TOKEN=dev-token
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
export JOB_SERVICE_URL=http://localhost:9085
export PORT=8001
```

## Data Flow

### 1. Initial Sync (Startup)
```
Job Service API → Paginated Fetch → Normalize Fields → Format Text → 
Generate Embeddings → Add to FAISS → Index Persisted
```

### 2. Real-time Updates (Kafka)
```
Job Created/Updated → Kafka Event → Consumer → Format → Embed → 
Update FAISS → Index Auto-saved
```

### 3. Search Flow
```
User Query → Generate Query Embedding (with "query:" prefix) → 
FAISS Similarity Search → Apply Filters → Rank Results → Return Top-K
```

### 4. Resume Match Flow
```
PDF Upload → Parse Resume → Extract Skills/Experience → Format Text → 
Generate Embedding → FAISS Search → Filter & Rank → Recommendations
```

## Model Information

### Sentence Transformer Model

**Model:** `sentence-transformers/all-MiniLM-L6-v2`
- **Size:** 80MB
- **Dimension:** 384
- **Max Sequence Length:** 256 tokens
- **Performance:** Fast inference (~50ms per embedding on M1 Mac)
- **Quality:** Good semantic understanding for job matching

### E5 Prefix Convention

The model uses **query/passage prefixes** for better semantic matching:
- **Queries** (resumes, search text): `"query: {text}"`
- **Documents** (job descriptions): `"passage: {text}"`

This improves retrieval quality by distinguishing query vs document embeddings.

### Model Alternatives

For production with higher quality needs:

| Model | Size | Dimension | Quality | Speed |
|-------|------|-----------|---------|-------|
| all-MiniLM-L6-v2 | 80MB | 384 | Good | Fast |
| all-mpnet-base-v2 | 420MB | 768 | Better | Medium |
| **e5-large** | 1.3GB | 1024 | Best | Slow |

⚠️ **Note:** Larger models require dedicated GPU server or cloud ML services (AWS SageMaker, Azure ML).

## Testing

### Manual Testing

```bash
# Test semantic search
curl -X POST http://localhost:8001/api/v1/recommendations/search \
  -H "Content-Type: application/json" \
  -d '{"query": "Python developer", "topK": 5}'

# Test with filters
curl -X POST http://localhost:8001/api/v1/recommendations/search \
  -H "Content-Type: application/json" \
  -d '{
    "query": "Senior Java developer",
    "topK": 5,
    "filters": {
      "locations": ["Ha Noi City"],
      "experienceLevels": ["SENIOR"]
    }
  }'

# Trigger manual sync
curl -X POST http://localhost:8001/api/v1/recommendations/admin/sync

# Check health
curl http://localhost:8001/health | jq
```

### Postman Collection

Import `api-docs/WorkFitAI-Platform.postman_collection.json` for complete API testing.

## Monitoring

### Logs

```bash
# View service logs
tail -f services/recommendation-engine/logs/service.log

# Watch FAISS index updates
tail -f logs/service.log | grep "FAISS"

# Monitor Kafka consumption
tail -f logs/service.log | grep "Kafka"
```

### Metrics

- **FAISS Index Size**: Check `/health` endpoint
- **Processing Time**: Included in all response bodies
- **Kafka Lag**: Monitor consumer group in Kafka UI (http://localhost:8080)

## Troubleshooting

### Model Loading Issues

**Problem:** Service crashes with exit code 139 (SIGSEGV)
**Cause:** Model too large for Docker container memory
**Solution:** Run locally with `./run-local.sh` or switch to smaller model

### Kafka Connection Failed

**Problem:** `kafka.errors.NoBrokersAvailable`
**Cause:** Kafka not running or wrong bootstrap servers
**Solution:** 
```bash
# Check Kafka is running
docker ps | grep kafka

# Verify Kafka is accessible
kafka-console-consumer --bootstrap-server localhost:9092 --list
```

### Job Service Sync Fails

**Problem:** `sync_jobs_from_service` returns 0 jobs
**Cause:** Job Service URL incorrect or no jobs in database
**Solutions:**
1. Check URL: `curl http://localhost:9085/job/public/jobs`
2. Verify jobs exist in Job Service database
3. Check API Gateway routing

### FAISS Index Corruption

**Problem:** Service fails to load saved index
**Solution:**
```bash
# Delete corrupted index
rm -rf services/recommendation-engine/data/faiss_index*

# Restart service (will rebuild from scratch)
./run-local.sh
```

### Uvicorn Import Errors

**Problem:** `ModuleNotFoundError: No module named 'fastapi'`
**Cause:** Virtual environment not activated or dependencies not installed
**Solution:**
```bash
cd services/recommendation-engine

# Recreate venv
rm -rf venv
python3.10 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

## Performance

### Benchmarks (M1 MacBook Pro, 16GB RAM)

| Operation | Time | Notes |
|-----------|------|-------|
| Model Loading | ~3s | One-time on startup |
| Generate Embedding | ~30ms | Single job/resume |
| FAISS Search (1000 jobs) | ~2ms | Top-20 results |
| Resume Parsing | ~100ms | PDF processing |
| End-to-End Search | ~655ms | Query → Results |
| Sync 30 Jobs | ~15s | Include embedding generation |

### Scalability

- **FAISS Index**: Supports millions of vectors with IVF indexing
- **Current Implementation**: IndexFlatIP (exact search, <10k jobs)
- **Production Recommendation**: Switch to IVF-FLAT or IVF-PQ for >10k jobs

## Production Optimization

### 1. Use IVF Index for Scale

```python
# Replace IndexFlatIP with IVF for faster search at scale
quantizer = faiss.IndexFlatIP(dimension)
index = faiss.IndexIVFFlat(quantizer, dimension, nlist=100)
index.train(embeddings)  # Required for IVF
index.nprobe = 10  # Number of clusters to search
```

### 2. Add Caching

```python
# Cache frequent queries
from functools import lru_cache

@lru_cache(maxsize=1000)
def cached_search(query_hash, top_k):
    return faiss_manager.search(...)
```

### 3. Batch Processing

```python
# Process multiple queries in batch
embeddings = model.encode_batch(queries, batch_size=32)
```

### 4. Add Authentication

```python
# JWT token validation
from fastapi import Depends, HTTPException
from fastapi.security import HTTPBearer

security = HTTPBearer()

@router.post("/search")
async def search(credentials: str = Depends(security)):
    # Validate JWT token
    ...
```

## Contributing

1. Follow PEP 8 style guide
2. Add type hints to all functions
3. Update tests for new features
4. Document API changes in this README

## License

MIT License - WorkFitAI Platform

## Support

- **Issues**: GitHub Issues
- **Docs**: `/docs` endpoint (Swagger UI)
- **Logs**: `logs/service.log`

Expected response:
```json
{
  "status": "healthy",
  "components": {
    "model": {"loaded": true},
    "faissIndex": {"totalJobs": 1234},
    "kafkaConsumer": {"connected": true}
  }
}
```

## 🐛 Troubleshooting

### Model not loading
- Check `MODEL_PATH` points to valid model directory
- Ensure model files are not corrupted
- Check available memory (model requires ~2GB RAM)

### FAISS index empty
- Verify Kafka connection
- Check Job Service is running
- Manually trigger initial sync

### Kafka consumer not receiving events
- Check Kafka broker is healthy
- Verify topic names match Job Service configuration
- Check consumer group offset

## 📚 API Documentation

Interactive API documentation available at:
- Swagger UI: `http://localhost:8001/docs`
- ReDoc: `http://localhost:8001/redoc`

## 🔐 Security

- API authentication (TODO: implement JWT)
- Rate limiting (TODO: implement)
- Input validation via Pydantic
- File upload size limits

## 📈 Performance

- **Model inference**: ~50-100ms per embedding (CPU)
- **FAISS search**: <10ms for 10k jobs
- **Resume parsing**: ~100-300ms per PDF
- **End-to-end recommendation**: ~200-500ms

## 🚧 Roadmap

- [ ] Implement API authentication
- [ ] Add caching layer (Redis)
- [ ] GPU support for faster inference
- [ ] Cross-encoder re-ranking
- [ ] User feedback loop
- [ ] A/B testing framework

## 📞 Support

For issues or questions, please refer to the main platform documentation.

## 📄 License

Part of WorkFitAI Platform. See main LICENSE file.
