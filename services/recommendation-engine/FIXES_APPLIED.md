# Recommendation Engine - Fixes Applied (Dec 20, 2025)

## Issues Fixed

### 1. ✅ Empty Recommendations Bug
**Problem**: Engine logs showed "✓ Found 20 matching jobs" but Job Service received empty arrays

**Root Cause**: 
- `by-profile` endpoint: ✅ Already fixed (uses `data.recommendations`)
- `similar-jobs` endpoint: ❌ Bug - looked for root `recommendations` instead of `data.recommendations`
- Field name mismatch: Used `"id"` instead of `"jobId"`

**Solution Applied**:
- Updated `RecommendationServiceImpl.getSimilarJobs()` to parse nested structure
- Changed field from `rec.get("id")` → `rec.get("jobId")`

**Files Modified**:
```
services/job-service/src/main/java/org/workfitai/jobservice/service/impl/RecommendationServiceImpl.java
  - Line 148-159: Added data wrapper extraction
  - Line 167: Changed "id" to "jobId"
```

---

### 2. ✅ Wrong Model Loading (384d → 1024d)
**Problem**: Engine loaded `all-MiniLM-L6-v2` (384d) instead of E5-Large (1024d)

**Root Cause**: 
- `.env.local` had `MODEL_PATH=intfloat/e5-large-v2` (downloads from HuggingFace)
- Vault was ENABLED with Docker path `/app/models/bi-encoder-e5-large`
- Local path `./models/bi-encoder-e5-large` exists but wasn't used

**Solution Applied**:
1. **Disabled Vault** for local development: `VAULT_ENABLED=false`
2. **Updated MODEL_PATH**: `./models/bi-encoder-e5-large` (local path)
3. **Deleted old FAISS index**: Removed 384d index to force rebuild

**Files Modified**:
```
services/recommendation-engine/.env.local
  - Line 13: VAULT_ENABLED=false (was true)
  - Line 24: MODEL_PATH=./models/bi-encoder-e5-large (was intfloat/e5-large-v2)
```

---

## Next Steps

### To Test Fixes:

1. **Kill current engine process**:
   ```bash
   lsof -ti:8000 | xargs kill -9
   ```

2. **Restart engine** (will rebuild FAISS with 1024d):
   ```bash
   cd services/recommendation-engine
   ./run-local.sh
   ```

3. **Verify model loading**:
   - Should see: `Loading model: ./models/bi-encoder-e5-large`
   - Should see: `Embedding dimension: 1024` (not 384!)

4. **Test endpoints**:
   ```bash
   # Test similar jobs
   curl "http://localhost:9085/job/public/recommendations/similar/bda37d0c-49f8-4d0e-ac08-0388a2fafdcc?topK=5"
   
   # Test for-me (CV-based)
   curl "http://localhost:9085/job/public/recommendations/for-me?topK=10"
   ```

5. **Expected Results**:
   - ✅ Non-empty `recommendations` array
   - ✅ Scores should be higher (better accuracy with E5-Large)
   - ✅ `totalResults` matches array length

---

## Configuration Reference

### Local Development (.env.local)
```dotenv
VAULT_ENABLED=false                    # Use .env.local instead of Vault
MODEL_PATH=./models/bi-encoder-e5-large  # Local path (1024d)
MODEL_DIMENSION=1024
KAFKA_BOOTSTRAP_SERVERS=localhost:9092  # Docker → localhost
```

### Docker/Production (Vault)
```json
{
  "model.path": "/app/models/bi-encoder-e5-large",  # Container path
  "model.dimension": "1024",
  "kafka.bootstrap.servers": "kafka:29092"  # Internal network
}
```

### Key Insight
- **Local**: Disable Vault, use relative paths (`./models/...`)
- **Docker**: Enable Vault, use absolute paths (`/app/models/...`)

---

## Files Changed Summary

1. **RecommendationServiceImpl.java** - Fixed similar-jobs response parsing
2. **.env.local** - Disabled Vault, fixed MODEL_PATH for local development
3. **data/faiss_index*** - Deleted old 384d index (will auto-rebuild with 1024d)

---

## Remaining Work

- [ ] Test cross-encoder reranking (when model files complete)
- [ ] Seed jobs into Job Service database
- [ ] Verify E5-Large accuracy improvements
- [ ] Update documentation with proper config patterns
