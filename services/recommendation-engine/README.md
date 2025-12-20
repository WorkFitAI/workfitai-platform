# WorkFitAI Recommendation Engine

Semantic job recommendation service using E5-Large embeddings and FAISS vector search.

## 📋 Overview

This service provides intelligent job recommendations by:
- Converting job descriptions and resumes to semantic embeddings using E5-Large model
- Performing fast similarity search using FAISS index
- Real-time synchronization with Job Service via Kafka events
- RESTful API for recommendation queries

## 🏗️ Architecture

```
┌─────────────────────────────────────────────┐
│       Recommendation Engine Service         │
│              (Python FastAPI)               │
├─────────────────────────────────────────────┤
│                                             │
│  ┌─────────────┐      ┌────────────────┐  │
│  │   FastAPI   │◄────►│  FAISS Index   │  │
│  │  REST API   │      │  (In-Memory)   │  │
│  └──────┬──────┘      └────────────────┘  │
│         │                                   │
│         ▼                                   │
│  ┌──────────────────────────────────────┐  │
│  │  Sentence Transformer (E5-Large)     │  │
│  │        1024-dim embeddings           │  │
│  └──────────────────────────────────────┘  │
│         ▲                                   │
│         │                                   │
│  ┌──────┴──────┐      ┌────────────────┐  │
│  │   Kafka     │      │  Resume Parser │  │
│  │  Consumer   │      │   (PyPDF2)     │  │
│  └─────────────┘      └────────────────┘  │
│                                             │
└─────────────────────────────────────────────┘
         ▲              │
         │ Events       │ HTTP
         │              ▼
    ┌────┴───┐    ┌──────────┐
    │ Kafka  │    │   Job    │
    └────────┘    │ Service  │
                  └──────────┘
```

## 🚀 Quick Start

### Using Docker Compose (Recommended)

```bash
# Start full platform with recommendation engine
./dev.sh full up

# Or start only recommendation engine
docker-compose --profile services up recommendation-engine

# View logs
./dev.sh full logs recommendation-engine
```

### Standalone (Development)

```bash
# 1. Install dependencies
cd services/recommendation-engine
pip install -r requirements.txt

# 2. Set environment variables
export MODEL_PATH=/path/to/models/bi-encoder-e5-large
export FAISS_INDEX_PATH=/path/to/data/faiss_index
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# 3. Run service
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

## 📡 API Endpoints

### Health Check
```bash
GET /health
```

### Recommend by Resume (PDF)
```bash
POST /api/v1/recommendations/by-resume
Content-Type: application/json

{
  "resumeFile": "base64_encoded_pdf",
  "topK": 20,
  "filters": {
    "locations": ["Ho Chi Minh City"],
    "employmentTypes": ["FULL_TIME"],
    "minSalary": 2000
  }
}
```

### Recommend by Profile (Text)
```bash
POST /api/v1/recommendations/by-profile
Content-Type: application/json

{
  "profileText": "Senior Backend Developer with 5+ years...",
  "skills": ["Java", "Spring Boot", "Kafka"],
  "topK": 20
}
```

### Find Similar Jobs
```bash
POST /api/v1/recommendations/similar-jobs
Content-Type: application/json

{
  "jobId": "uuid-reference-job",
  "topK": 10,
  "excludeSameCompany": true
}
```

### Semantic Search
```bash
POST /api/v1/recommendations/search
Content-Type: application/json

{
  "query": "remote python developer machine learning",
  "topK": 15
}
```

## 🔧 Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `MODEL_PATH` | `/app/models/bi-encoder-e5-large` | Path to E5-Large model |
| `MODEL_DIMENSION` | `1024` | Embedding dimension |
| `FAISS_INDEX_PATH` | `/app/data/faiss_index` | FAISS index storage path |
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka:29092` | Kafka broker address |
| `ENABLE_KAFKA_CONSUMER` | `true` | Enable real-time sync |
| `JOB_SERVICE_URL` | `http://job-service:9082` | Job Service URL |
| `DEFAULT_TOP_K` | `20` | Default number of results |
| `MAX_RESUME_SIZE_MB` | `5` | Max resume file size |

See [app/config.py](app/config.py) for full configuration.

## 📊 Data Flow

### 1. Job Creation Flow
```
Job Service → Kafka (job.created) → Recommendation Engine
                                           ↓
                                    Generate Embedding
                                           ↓
                                    Update FAISS Index
```

### 2. Recommendation Flow
```
User/API → Resume Upload → Parse PDF → Generate Embedding
                                             ↓
                                      FAISS Search
                                             ↓
                                      Return Job IDs
                                             ↓
Job Service ← Fetch Full Details ← Job IDs + Scores
```

## 🧪 Testing

```bash
# Run all tests
pytest

# Run with coverage
pytest --cov=app --cov-report=html

# Test specific module
pytest tests/test_embedding_service.py
```

## 📝 Project Structure

```
recommendation-engine/
├── app/
│   ├── __init__.py
│   ├── main.py                 # FastAPI application
│   ├── config.py               # Configuration management
│   ├── models/
│   │   ├── __init__.py
│   │   ├── requests.py         # Pydantic request models
│   │   └── responses.py        # Pydantic response models
│   ├── services/
│   │   ├── __init__.py
│   │   ├── embedding_service.py    # E5-Large embedding generation
│   │   ├── faiss_manager.py        # FAISS index operations
│   │   ├── resume_parser.py        # PDF resume parsing
│   │   ├── job_formatter.py        # Job text formatting
│   │   └── job_sync.py             # Initial sync from Job Service
│   ├── kafka_consumer/
│   │   ├── __init__.py
│   │   └── consumer.py         # Kafka event consumer
│   └── api/
│       ├── __init__.py
│       └── routes.py           # API route handlers
├── models/                     # Pre-trained models (mounted volume)
│   └── bi-encoder-e5-large/
├── data/                       # FAISS index persistence
├── tests/
├── requirements.txt
├── Dockerfile
├── .dockerignore
└── README.md
```

## 🔍 Monitoring

### Prometheus Metrics
Available at `:9090/metrics` (if enabled)

### Logs
Structured JSON logs with correlation IDs

### Health Check
```bash
curl http://localhost:8001/health
```

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
