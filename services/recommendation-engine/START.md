# 🚀 Quick Start Guide - Windows

Hướng dẫn chạy Recommendation Engine trên Windows (local development).

---

## 📋 Prerequisites

**Cần cài đặt trước:**
- ✅ Python 3.11+ ([Download](https://www.python.org/downloads/))
- ✅ Git ([Download](https://git-scm.com/download/win))
- ✅ Docker Desktop ([Download](https://www.docker.com/products/docker-desktop/))

**Kiểm tra version:**
```powershell
python --version    # Python 3.11.x hoặc mới hơn
git --version       # git version 2.x.x
docker --version    # Docker version 20.x.x
```

---

## 🏃‍♂️ Quick Start (PowerShell)

### Bước 1: Mở PowerShell tại thư mục recommendation-engine

```powershell
cd services\recommendation-engine
```

### Bước 2: Tạo Virtual Environment

```powershell
# Tạo virtual environment
python -m venv venv

# Kích hoạt virtual environment
.\venv\Scripts\Activate.ps1
```

**⚠️ Nếu gặp lỗi execution policy:**
```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

### Bước 3: Cài đặt Dependencies

```powershell
# Upgrade pip
python -m pip install --upgrade pip

# Cài đặt packages
pip install -r requirements.txt
```

⏱️ **Lưu ý:** Cài đặt lần đầu mất ~5-10 phút (tải PyTorch, transformers, faiss-cpu, etc.)

### Bước 4: Khởi động Docker Services

**Mở Terminal mới** (giữ nguyên PowerShell hiện tại):

```powershell
# Di chuyển về root project
cd ..\..

# Khởi động infrastructure services
.\dev.sh infra up
```

**Kiểm tra services đã chạy:**
```powershell
docker ps
```

Phải thấy các services sau:
- ✅ Vault (port 8200)
- ✅ Kafka (port 9092)
- ✅ Consul (port 8500)
- ✅ Job Service (port 9082)

### Bước 5: Tạo file .env.local

**Tại thư mục `recommendation-engine`**, tạo file `.env.local` với nội dung:

```env
# Application
ENVIRONMENT=local
PORT=8000
HOST=0.0.0.0
LOG_LEVEL=INFO
API_VERSION=v1
SERVICE_NAME=recommendation-engine

# Vault (Disabled for local)
VAULT_ENABLED=false
VAULT_ADDR=http://localhost:8200
VAULT_TOKEN=dev-token

# Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
KAFKA_GROUP_ID=recommendation-service-local
KAFKA_AUTO_OFFSET_RESET=earliest

# Job Service
JOB_SERVICE_URL=http://localhost:9082

# ML Model Configuration
MODEL_PATH=./models/bi-encoder-e5-large
MODEL_NAME=intfloat/e5-large-v2
MODEL_DIMENSION=1024
MAX_SEQ_LENGTH=512
BATCH_SIZE=32

# Cross-Encoder Reranking
ENABLE_RERANKING=true
CROSS_ENCODER_PATH=./models/cross-encoder
RERANK_TOP_K=50
RERANK_TOP_N=20

# FAISS Index Configuration
FAISS_INDEX_PATH=./data/faiss_index
ENABLE_INITIAL_SYNC=true
INITIAL_SYNC_LIMIT=1000

# Resume Parser
MIN_TEXT_LENGTH=50
EXTRACT_SKILLS=true
EXTRACT_EXPERIENCE=true
EXTRACT_EDUCATION=true

# Search Configuration
DEFAULT_TOP_K=20
MAX_TOP_K=100
MIN_SCORE_THRESHOLD=0.5

# API Settings
ENABLE_CORS=true
CORS_ORIGINS=["http://localhost:3000","http://localhost:9085"]
```

### Bước 6: Tạo thư mục cần thiết

```powershell
# Tạo folders
New-Item -ItemType Directory -Force -Path data
New-Item -ItemType Directory -Force -Path logs
New-Item -ItemType Directory -Force -Path models
```

### Bước 7: Kiểm tra Model

**Kiểm tra model đã tồn tại chưa:**
```powershell
Test-Path models\bi-encoder-e5-large
Test-Path models\cross-encoder
```

**Nếu chưa có model:**
- Model `bi-encoder-e5-large` sẽ tự động download từ Hugging Face lần đầu chạy (~2GB)
- Model `cross-encoder` cần được train hoặc download riêng

### Bước 8: Kiểm tra FAISS Index compatibility

```powershell
# Đọc model dimension hiện tại
$currentDim = 1024  # Default

# Nếu có file dimension cũ
if (Test-Path data\.model_dimension) {
    $lastDim = Get-Content data\.model_dimension
    if ($lastDim -ne $currentDim) {
        Write-Host "⚠️  Model dimension changed: $lastDim → $currentDim" -ForegroundColor Yellow
        Write-Host "🗑️  Removing old FAISS index..." -ForegroundColor Yellow
        Remove-Item data\faiss_index* -Force -ErrorAction SilentlyContinue
        Write-Host "✅ Old index removed" -ForegroundColor Green
    }
}

# Save dimension
$currentDim | Out-File -FilePath data\.model_dimension -Encoding utf8
```

### Bước 9: Chạy Server 🚀

```powershell
# Chạy server với uvicorn
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload --log-level info
```

**Server sẽ khởi động tại:** `http://localhost:8000`

**API Docs:** `http://localhost:8000/docs` (Swagger UI)

---

## 🛑 Dừng Server

Nhấn `Ctrl+C` trong PowerShell để dừng server.

---

## 📝 Commands Cheat Sheet

### Activate Virtual Environment
```powershell
# PowerShell
.\venv\Scripts\Activate.ps1

# CMD
venv\Scripts\activate.bat
```

### Deactivate Virtual Environment
```powershell
deactivate
```

### Cài đặt/Update Dependencies
```powershell
pip install -r requirements.txt
```

### Xem Logs
```powershell
Get-Content logs\app.log -Wait  # Live tail logs
```

### Clean FAISS Index
```powershell
Remove-Item data\faiss_index* -Force
```

### Check Docker Services
```powershell
# List running containers
docker ps

# Check specific service
docker ps --filter "name=vault"
docker ps --filter "name=kafka"
docker ps --filter "name=job-service"
```

### Restart Docker Services
```powershell
cd ..\..
.\dev.sh infra restart
```

---

## 🔧 Troubleshooting

### 1. Lỗi: "Execution Policy" khi activate virtual environment

**Nguyên nhân:** Windows PowerShell mặc định block scripts.

**Giải pháp:**
```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

### 2. Lỗi: "Python not found" hoặc "pip not found"

**Nguyên nhân:** Python chưa được thêm vào PATH.

**Giải pháp:**
1. Cài lại Python và check ☑️ "Add Python to PATH"
2. Hoặc thêm thủ công vào PATH:
   - Search "Environment Variables" trong Windows
   - Thêm `C:\Users\<YourName>\AppData\Local\Programs\Python\Python311` vào PATH

### 3. Lỗi: "Port 8000 already in use"

**Nguyên nhân:** Port đã bị chiếm bởi process khác.

**Giải pháp:**
```powershell
# Tìm process đang dùng port 8000
netstat -ano | findstr :8000

# Kill process (thay <PID> bằng số trong cột cuối)
taskkill /PID <PID> /F

# Hoặc đổi port trong .env.local
PORT=8001
```

### 4. Lỗi: Kafka connection refused

**Nguyên nhân:** Kafka chưa khởi động hoặc chưa ready.

**Giải pháp:**
```powershell
# Check Kafka logs
docker logs kafka

# Restart Kafka
cd ..\..
.\dev.sh infra restart kafka
```

### 5. Lỗi: "No module named 'torch'"

**Nguyên nhân:** Dependencies chưa cài đặt đúng.

**Giải pháp:**
```powershell
# Activate virtual environment trước
.\venv\Scripts\Activate.ps1

# Reinstall
pip install --upgrade pip
pip install -r requirements.txt
```

### 6. Lỗi: FAISS dimension mismatch

**Nguyên nhân:** Đổi model nhưng FAISS index còn dimension cũ.

**Giải pháp:**
```powershell
# Xóa index cũ
Remove-Item data\faiss_index* -Force

# Restart server (sẽ rebuild index)
python -m uvicorn app.main:app --reload
```

### 7. Lỗi: "Cannot connect to Job Service"

**Nguyên nhân:** Job Service chưa chạy hoặc URL sai.

**Giải pháp:**
```powershell
# Test Job Service
curl http://localhost:9082/actuator/health

# Nếu không chạy, start job-service
cd ..\..
.\dev.sh full up job-service
```

### 8. Lỗi: Model download quá chậm

**Nguyên nhân:** Hugging Face download từ server nước ngoài.

**Giải pháp:**
- Sử dụng VPN
- Hoặc download model trước bằng script:

```python
# download_model.py
from sentence_transformers import SentenceTransformer

print("Downloading bi-encoder model...")
model = SentenceTransformer('intfloat/e5-large-v2')
model.save('./models/bi-encoder-e5-large')
print("✅ Model downloaded successfully")
```

Chạy:
```powershell
python download_model.py
```

---

## 📊 Health Check

Sau khi server khởi động, test các endpoints:

```powershell
# Health check
curl http://localhost:8000/health

# API docs
Start-Process http://localhost:8000/docs

# Test recommendation (cần có CV file)
curl -X POST http://localhost:8000/api/v1/recommendations/by-resume `
  -H "Content-Type: multipart/form-data" `
  -F "file=@sample-cv.pdf" `
  -F "topK=10"
```

---

## 🎯 Next Steps

1. ✅ Test recommendation API với Postman
2. ✅ Xem API documentation tại `/docs`
3. ✅ Monitor logs trong folder `logs/`
4. ✅ Tích hợp với frontend/application-service

---

## 📚 Useful Links

- **API Docs:** http://localhost:8000/docs
- **Health Check:** http://localhost:8000/health
- **Swagger UI:** http://localhost:8000/docs
- **ReDoc:** http://localhost:8000/redoc
- **Kafka UI:** http://localhost:8080
- **Consul UI:** http://localhost:8500

---

## 💡 Tips

### Chạy nhanh bằng Batch Script

Tạo file `start.bat` trong thư mục `recommendation-engine`:

```batch
@echo off
echo 🚀 Starting Recommendation Engine...

REM Activate virtual environment
call venv\Scripts\activate.bat

REM Run server
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload --log-level info
```

Sau đó chỉ cần double-click `start.bat` để chạy!

### Chạy background (không block terminal)

```powershell
# Start server in background
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PWD'; .\venv\Scripts\Activate.ps1; python -m uvicorn app.main:app --host 0.0.0.0 --port 8000"
```

---

## ❓ Cần Help?

- 📖 Xem [README_NEW.md](./README_NEW.md) để hiểu architecture
- 📖 Xem [ACADEMIC_MODEL_APPLICATION.md](./ACADEMIC_MODEL_APPLICATION.md) để hiểu model
- 🐛 Check logs trong folder `logs/`
- 💬 Hỏi team trên Slack/Discord

---

**Happy Coding! 🚀**
