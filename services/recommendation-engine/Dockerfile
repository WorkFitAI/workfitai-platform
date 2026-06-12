# WorkFitAI Recommendation Engine Dockerfile
# Python 3.10 with FastAPI, Sentence Transformers, and FAISS

FROM python:3.10-slim

# Set working directory
WORKDIR /app

# Install system dependencies for PDF parsing and ML libraries
RUN apt-get update && apt-get install -y \
    build-essential \
    curl \
    git \
    libpoppler-cpp-dev \
    poppler-utils \
    && rm -rf /var/lib/apt/lists/*

# Upgrade pip in a dedicated layer — cached separately from requirements
RUN pip install --no-cache-dir --upgrade pip

# Copy requirements before app code so this layer is only invalidated when
# requirements.txt changes, not on every code edit.
COPY requirements.txt .

# BuildKit cache mount: downloaded wheels are stored in the BuildKit cache
# (not in the final image).  When this layer reruns for any reason — base image
# update, pruned layer cache — pip reads wheels from the cache instead of
# re-downloading everything from PyPI.
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install -r requirements.txt

# Copy application code
COPY app/ ./app/
COPY src/ ./src/
COPY config/ ./config/

# Create directories for models and data
RUN mkdir -p /app/models/job-recommend /app/models/cv-refer /app/data /app/logs

# Expose FastAPI port
EXPOSE 8000

# Health check (increase start-period for model loading)
HEALTHCHECK --interval=30s --timeout=10s --start-period=120s --retries=3 \
    CMD curl -f http://localhost:8000/health || exit 1

# Set environment variables
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    MODEL_PATH=/app/models \
    FAISS_INDEX_PATH=/app/data/faiss_index \
    LOG_LEVEL=INFO

# Run FastAPI with Uvicorn (increase timeout for model loading)
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1", "--log-level", "info", "--timeout-keep-alive", "300"]
