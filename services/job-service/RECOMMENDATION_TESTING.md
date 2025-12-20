# Test Recommendation Integration - Job Service

## 📋 Test Flow
```
User (with JWT) → Job Service /for-me → CV Service (get CVs) → 
Recommendation Engine (get scores) → Job Service (fetch job details) → Response
```

## 🧪 Test Commands

### 1. Test Get Recommendations for Authenticated User
```bash
# For home page personalized recommendations
curl -X GET "http://localhost:9085/job/public/recommendations/for-me?topK=20" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  | jq

# With filters
curl -X GET "http://localhost:9085/job/public/recommendations/for-me?topK=10&locations=Ha%20Noi%20City&experienceLevels=SENIOR" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  | jq
```

**Expected Response:**
```json
{
  "status": 200,
  "message": "Recommendations fetched successfully based on your CV",
  "data": {
    "recommendations": [
      {
        "job": {
          "jobId": "uuid",
          "title": "Senior Java Developer",
          "company": {...},
          "location": "Ha Noi City",
          "salaryMin": 2000,
          "salaryMax": 3000,
          "skills": ["Java", "Spring Boot"],
          ...
        },
        "score": 0.85,
        "rank": 1
      },
      {
        "job": {...},
        "score": 0.78,
        "rank": 2
      }
    ],
    "totalResults": 20,
    "processingTime": "655ms"
  }
}
```

### 2. Test Custom Profile Recommendations
```bash
curl -X POST "http://localhost:9085/job/public/recommendations/by-profile" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "profileText": "Senior Java Developer with 5 years experience in Spring Boot, Microservices, Docker, Kubernetes. Strong background in distributed systems and cloud architecture.",
    "topK": 15,
    "filters": {
      "locations": ["Ha Noi City", "Ho Chi Minh City"],
      "experienceLevels": ["SENIOR"],
      "minSalary": 2000
    }
  }' | jq
```

### 3. Test Similar Jobs
```bash
# Get similar jobs to a specific job
curl -X GET "http://localhost:9085/job/public/recommendations/similar/JOB_UUID?topK=10&excludeSameCompany=true" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  | jq
```

## 🔍 Verification Checklist

### ✅ Job Service Integration
- [ ] Feign clients configured correctly
- [ ] Service URLs in application.yml
- [ ] Repository method `findActiveJobsByIds()` works
- [ ] JobMapper `toResJobDTO()` works

### ✅ CV Service Integration
- [ ] Endpoint `/api/v1/cv/candidate/{username}` returns CVs
- [ ] Response format: `{data: {result: [cvs], meta: {...}}}`
- [ ] Latest CV (first in list) is selected
- [ ] Sections (skills, experience, education) are extracted

### ✅ Recommendation Engine Integration
- [ ] Endpoint `/api/v1/recommendations/by-profile` works
- [ ] Returns job IDs with scores
- [ ] Scores are between 0-1
- [ ] Results sorted by rank

### ✅ Response Format
- [ ] Jobs sorted by score (highest first)
- [ ] Each job has full details from database
- [ ] Score and rank included for each job
- [ ] Processing time included
- [ ] totalResults accurate

## 🐛 Debugging

### Check Job Service Logs
```bash
docker logs job-service -f
```

Look for:
- "Getting recommendations for user: {username}"
- "Found X CVs for user, using the most recent one"
- Feign call logs

### Check CV Service
```bash
# Manual test CV endpoint
curl "http://localhost:9083/api/v1/cv/candidate/testuser?page=0&size=10" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" | jq
```

### Check Recommendation Engine
```bash
# Health check
curl http://localhost:8001/health | jq

# Manual test
curl -X POST http://localhost:8001/api/v1/recommendations/by-profile \
  -H "Content-Type: application/json" \
  -d '{
    "profileText": "Java Spring Boot developer",
    "topK": 5
  }' | jq
```

## 📊 Expected Behavior for `/for-me` Endpoint

1. **Get authenticated username** from JWT token
2. **Fetch all CVs** for user from CV Service (sorted newest first)
3. **Extract latest CV** (index 0)
4. **Format CV text** from sections (skills, experience, education, projects, languages)
5. **Call Recommendation Engine** with CV profile text + filters
6. **Receive job IDs + scores** sorted by relevance
7. **Fetch full job details** from database (only PUBLISHED & active)
8. **Map to response DTO** with job details + score + rank
9. **Return sorted list** (by rank from engine)

## 🎯 Sorting Guarantee

Jobs are sorted **twice**:
1. **Recommendation Engine** returns results sorted by similarity score
2. **Job Service** sorts again by `rank` field: 
   ```java
   .sorted(Comparator.comparingInt(JobRecommendation::getRank))
   ```

This ensures jobs are always in correct order (highest score first).

## 🔧 Configuration Required

### application.yml (job-service)
```yaml
service:
  recommendation:
    url: http://localhost:8001
  cv:
    url: http://localhost:9083
  auth:
    url: http://localhost:9080
```

### Via API Gateway
If calling through API Gateway (port 9085):
- Job Service: `http://localhost:9085/job/*`
- Routes automatically to internal services

## 🚀 Quick Test Script

```bash
#!/bin/bash

# 1. Login and get token
TOKEN=$(curl -X POST http://localhost:9085/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"password"}' \
  | jq -r '.data.token')

echo "Token: $TOKEN"

# 2. Get personalized recommendations
echo "\n=== Testing /for-me endpoint ==="
curl -X GET "http://localhost:9085/job/public/recommendations/for-me?topK=10" \
  -H "Authorization: Bearer $TOKEN" \
  | jq '.data.recommendations[] | {title: .job.title, score: .score, rank: .rank}'

# 3. Verify sorting (scores should decrease)
echo "\n=== Verifying score order ==="
curl -X GET "http://localhost:9085/job/public/recommendations/for-me?topK=5" \
  -H "Authorization: Bearer $TOKEN" \
  | jq '.data.recommendations | map(.score)'
```

Expected output: `[0.85, 0.78, 0.65, 0.52, 0.41]` (decreasing order)
