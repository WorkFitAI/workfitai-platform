# Recommendation Flow - Job Service Integration

## 🔄 Complete Data Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                    GET /job/public/recommendations/for-me       │
│                    Authorization: Bearer JWT_TOKEN              │
└────────────────────────────────┬────────────────────────────────┘
                                 │
                                 ▼
                    ┌────────────────────────┐
                    │   RecommendationController   │
                    │   - Extract username from JWT │
                    │   - Parse filters            │
                    └────────────┬───────────────┘
                                 │
                                 ▼
                    ┌────────────────────────┐
                    │  RecommendationService  │
                    │  getRecommendationsByCV │
                    └────────────┬───────────────┘
                                 │
                ┌────────────────┴────────────────┐
                │                                 │
                ▼                                 ▼
    ┌───────────────────┐              ┌──────────────────┐
    │   CVFeignClient   │              │  (Wait for CV)   │
    │ GET /cv/candidate │              └──────────────────┘
    │    /{username}    │
    └─────────┬─────────┘
              │
              ▼
    ┌───────────────────┐
    │   CV Service      │
    │ - Get all CVs     │
    │ - Sort by newest  │
    │ - Return paginated│
    └─────────┬─────────┘
              │
              ▼
    Response: {
      data: {
        result: [CV1, CV2, ...],
        meta: {page, pages, total}
      }
    }
              │
              ▼
    ┌───────────────────┐
    │  Extract Latest   │
    │  CV (index 0)     │
    │  - headline       │
    │  - summary        │
    │  - sections:      │
    │    * skills       │
    │    * experience   │
    │    * education    │
    │    * projects     │
    │    * languages    │
    └─────────┬─────────┘
              │
              ▼
    Profile Text: 
    "Senior Java Developer
    
    Summary: 5 years experience...
    
    Skills: Java, Spring Boot, Docker...
    
    Experience: ..."
              │
              ▼
    ┌─────────────────────────────┐
    │  RecommendationFeignClient   │
    │ POST /by-profile             │
    │  - profileText               │
    │  - topK                      │
    │  - filters                   │
    └─────────┬───────────────────┘
              │
              ▼
    ┌─────────────────────────────┐
    │  Recommendation Engine       │
    │ 1. Generate query embedding  │
    │ 2. FAISS similarity search   │
    │ 3. Apply filters            │
    │ 4. Sort by score            │
    └─────────┬───────────────────┘
              │
              ▼
    Response: {
      recommendations: [
        {id: "uuid1", score: 0.85, rank: 1},
        {id: "uuid2", score: 0.78, rank: 2},
        {id: "uuid3", score: 0.65, rank: 3}
      ],
      totalResults: 20,
      processingTime: "655ms"
    }
              │
              ▼
    ┌─────────────────────────────┐
    │  JobRepository               │
    │  findActiveJobsByIds()       │
    │  - WHERE jobId IN (...)      │
    │  - AND status = PUBLISHED    │
    │  - AND isDeleted = false     │
    │  - AND expiresAt > NOW()     │
    └─────────┬───────────────────┘
              │
              ▼
    ┌─────────────────────────────┐
    │  Map to Response DTO         │
    │  For each job:               │
    │  - Convert to ResJobDTO      │
    │  - Add score from engine     │
    │  - Add rank from engine      │
    │  - Sort by rank              │
    └─────────┬───────────────────┘
              │
              ▼
    ┌─────────────────────────────┐
    │  Final Response              │
    │  {                           │
    │    recommendations: [        │
    │      {                       │
    │        job: {full details},  │
    │        score: 0.85,          │
    │        rank: 1               │
    │      },                      │
    │      ...                     │
    │    ],                        │
    │    totalResults: 20,         │
    │    processingTime: "655ms"   │
    │  }                           │
    └──────────────────────────────┘
```

## 📊 Response Sorting Guarantee

### Double Sorting Mechanism:

1. **Recommendation Engine** (First Sort):
   - FAISS returns top-K most similar jobs
   - Sorted by cosine similarity score (0-1)
   - Assigns rank (1, 2, 3, ...)

2. **Job Service** (Second Sort):
   ```java
   .sorted(Comparator.comparingInt(JobRecommendation::getRank))
   ```
   - Ensures final response maintains engine's order
   - Even if database fetch is unordered

### Result:
- Jobs are **ALWAYS** sorted by relevance score
- Highest matching job appears first (rank 1, score ~0.85)
- Lowest matching job appears last (rank N, score ~0.2)

## 🎯 Use Cases

### 1. Home Page - Personalized Feed
```http
GET /job/public/recommendations/for-me?topK=20
Authorization: Bearer {token}
```
**Returns:** Top 20 jobs matching user's CV, sorted by relevance

### 2. Search Page - Enhanced Search
```http
GET /job/public/recommendations/for-me?topK=50&locations=Ha%20Noi&experienceLevels=SENIOR
Authorization: Bearer {token}
```
**Returns:** Top 50 SENIOR jobs in Ha Noi matching user's profile

### 3. Job Detail - Similar Jobs
```http
GET /job/public/recommendations/similar/{jobId}?topK=10
```
**Returns:** 10 similar jobs to the current job

## 🔍 Data Sources

| Component | Data Type | Source |
|-----------|-----------|--------|
| Job IDs + Scores | ML Predictions | Recommendation Engine (FAISS) |
| Job Details | Full Records | Job Service Database (PostgreSQL) |
| CV Content | User Resume | CV Service (MongoDB) |
| User Context | Authentication | JWT Token |

## ⚙️ Performance Metrics

| Stage | Expected Time | Notes |
|-------|--------------|-------|
| CV Fetch | ~50ms | HTTP call to CV Service |
| CV Parsing | ~10ms | Extract sections from latest CV |
| Recommendation Engine | ~655ms | Embedding + FAISS search |
| Database Fetch | ~100ms | Fetch 20 full job records |
| Mapping | ~20ms | Convert entities to DTOs |
| **Total** | **~835ms** | End-to-end for 20 recommendations |

## 🛡️ Error Handling

```java
try {
    // 1. Fetch CV
    String cvText = fetchCVProfileText(username);
    if (cvText == null) {
        return emptyResponse(); // No CV found
    }
    
    // 2. Call recommendation engine
    Map response = recommendationFeignClient.getRecommendations(...);
    if (response == null) {
        return emptyResponse(); // Engine failed
    }
    
    // 3. Fetch job details
    List<Job> jobs = jobRepository.findActiveJobsByIds(jobIds);
    // Returns only PUBLISHED & active jobs
    
} catch (FeignException e) {
    log.error("Feign call failed", e);
    throw new RuntimeException("Failed to get recommendations");
}
```

## 📝 Response Format

```json
{
  "status": 200,
  "message": "Recommendations fetched successfully based on your CV",
  "data": {
    "recommendations": [
      {
        "job": {
          "jobId": "31c3f83a-0b89-4bac-bf31-954bef0c480a",
          "title": "Senior Backend Developer",
          "company": {
            "name": "VNG Corporation",
            "logo": "https://..."
          },
          "location": "Ha Noi City",
          "salaryMin": 2000,
          "salaryMax": 3000,
          "currency": "USD",
          "experienceLevel": "SENIOR",
          "employmentType": "FULL_TIME",
          "skills": [
            {"skillId": "1", "name": "Java"},
            {"skillId": "2", "name": "Spring Boot"}
          ],
          "shortDescription": "We are looking for...",
          "views": 125,
          "totalApplications": 15
        },
        "score": 0.8543,
        "rank": 1
      }
    ],
    "totalResults": 20,
    "processingTime": "655ms"
  }
}
```

## ✅ Integration Checklist

- [x] CVFeignClient configured
- [x] RecommendationFeignClient configured
- [x] Service URLs in application.yml
- [x] @EnableFeignClients in main application
- [x] Repository method for batch job fetch
- [x] JobMapper for entity → DTO conversion
- [x] Double sorting (engine + service)
- [x] Error handling for missing CV
- [x] Error handling for engine failures
- [x] Filter active jobs only (PUBLISHED, not deleted, not expired)
- [x] JWT authentication required
- [x] Logging for debugging
- [ ] Unit tests for service layer
- [ ] Integration tests end-to-end
