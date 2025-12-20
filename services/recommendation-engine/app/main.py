"""
FastAPI application for WorkFitAI Recommendation Engine
Main entry point for the service
"""

import logging
import asyncio
from contextlib import asynccontextmanager
from typing import Dict, Any

from fastapi import FastAPI, Request, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from fastapi.exceptions import RequestValidationError
import uvicorn

from app.config import get_settings
from app.api.routes import router

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Global state
app_state = {
    "model": None,
    "faiss_manager": None,
    "kafka_consumer": None,
    "resume_parser": None
}


@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    Startup and shutdown events
    """
    settings = get_settings()
    logger.info(f"Starting {settings.SERVICE_NAME} v{settings.VERSION}")
    logger.info(f"Environment: {settings.ENVIRONMENT}")
    
    try:
        # Initialize model
        logger.info("Loading Sentence Transformer model...")
        from app.services.embedding_service import EmbeddingGenerator
        app_state["model"] = EmbeddingGenerator(settings.MODEL_PATH)
        logger.info(f"✓ Model loaded: {settings.MODEL_PATH}")
        
        # Initialize FAISS manager
        logger.info("Initializing FAISS index...")
        from app.services.faiss_manager import FAISSIndexManager
        app_state["faiss_manager"] = FAISSIndexManager(
            dimension=settings.MODEL_DIMENSION,
            index_path=settings.FAISS_INDEX_PATH if settings.ENABLE_INDEX_PERSISTENCE else None
        )
        logger.info(f"✓ FAISS index initialized with {app_state['faiss_manager'].index.ntotal} jobs")
        
        # Initialize resume parser
        logger.info("Initializing Resume Parser...")
        from app.services.resume_parser import ResumeParser
        app_state["resume_parser"] = ResumeParser()
        logger.info("✓ Resume Parser initialized")
        
        # Initial sync from Job Service (if enabled)
        if settings.ENABLE_INITIAL_SYNC and app_state["faiss_manager"].index.ntotal == 0:
            logger.info("Starting initial sync from Job Service...")
            try:
                from app.services.job_sync import sync_jobs_from_service
                await sync_jobs_from_service(
                    app_state["faiss_manager"],
                    app_state["model"]
                )
                logger.info("✓ Initial sync completed")
            except Exception as e:
                logger.error(f"Initial sync failed: {e}", exc_info=True)
                logger.warning("Service will continue without initial data")
        
        # Start Kafka consumer (if enabled)
        if settings.ENABLE_KAFKA_CONSUMER:
            logger.info("Starting Kafka consumer...")
            try:
                from app.kafka_consumer.consumer import JobEventConsumer
                app_state["kafka_consumer"] = JobEventConsumer(
                    faiss_manager=app_state["faiss_manager"],
                    model=app_state["model"]
                )
                # Start consumer in background task
                asyncio.create_task(
                    asyncio.to_thread(app_state["kafka_consumer"].start_consuming)
                )
                logger.info("✓ Kafka consumer started")
            except Exception as e:
                logger.error(f"Failed to start Kafka consumer: {e}", exc_info=True)
                logger.warning("Service will continue without Kafka integration")
        
        logger.info("=" * 60)
        logger.info(f"🚀 {settings.SERVICE_NAME} is ready!")
        logger.info(f"📍 Listening on http://{settings.HOST}:{settings.PORT}")
        logger.info(f"📊 FAISS Index: {app_state['faiss_manager'].index.ntotal} jobs indexed")
        logger.info("=" * 60)
        
        yield  # Application runs
        
    except Exception as e:
        logger.error(f"Failed to start application: {e}", exc_info=True)
        raise
    
    finally:
        # Shutdown
        logger.info("Shutting down...")
        
        # Save FAISS index
        if app_state["faiss_manager"] and settings.ENABLE_INDEX_PERSISTENCE:
            try:
                app_state["faiss_manager"].save_index()
                logger.info("✓ FAISS index saved")
            except Exception as e:
                logger.error(f"Failed to save index: {e}")
        
        # Stop Kafka consumer
        if app_state["kafka_consumer"]:
            try:
                app_state["kafka_consumer"].stop()
                logger.info("✓ Kafka consumer stopped")
            except Exception as e:
                logger.error(f"Failed to stop Kafka consumer: {e}")
        
        logger.info("Shutdown complete")


# Create FastAPI app
app = FastAPI(
    title="WorkFitAI Recommendation Engine",
    description="Semantic job recommendation service using E5-Large embeddings and FAISS",
    version=get_settings().VERSION,
    lifespan=lifespan,
    docs_url="/docs",
    redoc_url="/redoc",
    openapi_url="/openapi.json"
)

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # TODO: Configure properly for production
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# Exception handlers
@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    """Handle validation errors"""
    return JSONResponse(
        status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
        content={
            "success": False,
            "error": "Validation Error",
            "details": exc.errors()
        }
    )


@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    """Handle unexpected errors"""
    logger.error(f"Unhandled exception: {exc}", exc_info=True)
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content={
            "success": False,
            "error": "Internal Server Error",
            "message": str(exc) if not get_settings().is_production() else "An error occurred"
        }
    )


# Include API routes
app.include_router(router)


# Health check endpoint
@app.get("/health", tags=["Health"])
async def health_check() -> Dict[str, Any]:
    """
    Health check endpoint
    Returns status of all components
    """
    settings = get_settings()
    
    health_status = {
        "status": "healthy",
        "service": settings.SERVICE_NAME,
        "version": settings.VERSION,
        "environment": settings.ENVIRONMENT,
        "components": {
            "model": {
                "loaded": app_state["model"] is not None,
                "path": settings.MODEL_PATH if app_state["model"] else None
            },
            "faissIndex": {
                "loaded": app_state["faiss_manager"] is not None,
                "totalJobs": app_state["faiss_manager"].index.ntotal if app_state["faiss_manager"] else 0,
                "dimension": settings.MODEL_DIMENSION
            },
            "resumeParser": {
                "loaded": app_state["resume_parser"] is not None
            },
            "kafkaConsumer": {
                "enabled": settings.ENABLE_KAFKA_CONSUMER,
                "connected": app_state["kafka_consumer"] is not None,
                "topics": settings.get_kafka_topics() if settings.ENABLE_KAFKA_CONSUMER else []
            }
        }
    }
    
    # Check if critical components are loaded
    if not app_state["model"] or not app_state["faiss_manager"]:
        health_status["status"] = "unhealthy"
        return JSONResponse(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            content=health_status
        )
    
    return health_status


# Root endpoint
@app.get("/", tags=["Root"])
async def root():
    """Root endpoint"""
    settings = get_settings()
    return {
        "service": settings.SERVICE_NAME,
        "version": settings.VERSION,
        "status": "running",
        "docs": "/docs",
        "health": "/health"
    }


# Make app_state accessible to routes
app.state.model = lambda: app_state["model"]
app.state.faiss_manager = lambda: app_state["faiss_manager"]
app.state.resume_parser = lambda: app_state["resume_parser"]


if __name__ == "__main__":
    settings = get_settings()
    uvicorn.run(
        "app.main:app",
        host=settings.HOST,
        port=settings.PORT,
        log_level=settings.LOG_LEVEL.lower(),
        reload=not settings.is_production()
    )
