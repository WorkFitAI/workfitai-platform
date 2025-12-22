@echo off
REM ========================================
REM Quick Start Script for Windows
REM Recommendation Engine - Local Mode
REM ========================================

echo.
echo 🚀 Starting Recommendation Engine (Local Mode)
echo ================================================
echo.

REM Check if virtual environment exists
if not exist "venv\Scripts\activate.bat" (
    echo ❌ Virtual environment not found!
    echo Please create it first:
    echo    python -m venv venv
    echo.
    pause
    exit /b 1
)

REM Activate virtual environment
echo ✅ Activating virtual environment...
call venv\Scripts\activate.bat

REM Check if .env.local exists
if not exist ".env.local" (
    echo ❌ .env.local not found!
    echo Please create .env.local file first
    echo See START.md for instructions
    echo.
    pause
    exit /b 1
)

REM Create necessary directories
if not exist "data" mkdir data
if not exist "logs" mkdir logs
if not exist "models" mkdir models

REM Load environment variables (optional check)
echo ✅ Loading configuration from .env.local
echo.

REM Print configuration
echo 📋 Configuration:
echo   Environment: local
echo   Port: 8000
echo   Model Path: ./models/bi-encoder-e5-large
echo   FAISS Index: ./data/faiss_index
echo.

REM Start server
echo 🚀 Starting server...
echo Press Ctrl+C to stop
echo.
echo 📍 Server will be available at: http://localhost:8000
echo 📖 API Docs: http://localhost:8000/docs
echo.

REM Run uvicorn
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload --log-level info

REM If server stops, wait for user
echo.
echo Server stopped.
pause
