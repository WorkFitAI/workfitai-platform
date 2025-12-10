# Application Service

Job application management service for the WorkFitAI Platform.

## Overview

The Application Service handles the complete lifecycle of job applications:

- Candidates submit applications for jobs
- HR reviews and updates application status
- All state changes are published as events for downstream services

## Architecture

### Event-Driven Design

This service uses **Kafka** for all cross-service communication instead of synchronous REST calls (Feign).

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   Frontend      │────▶│  application-   │────▶│     KAFKA       │
│   (Candidate)   │     │     service     │     │                 │
└─────────────────┘     └─────────────────┘     └────────┬────────┘
                                                         │
                        ┌────────────────────────────────┼────────────────────────────────┐
                        │                                │                                │
                        ▼                                ▼                                ▼
               ┌─────────────────┐            ┌─────────────────┐            ┌─────────────────┐
               │   job-service   │            │ notification-   │            │   analytics-    │
               │ (update count)  │            │    service      │            │    service      │
               └─────────────────┘            └─────────────────┘            └─────────────────┘
```

### Why Event-Driven?

| Aspect          | Synchronous (Feign)                | Event-Driven (Kafka)          |
| --------------- | ---------------------------------- | ----------------------------- |
| **Coupling**    | Tight - services must be available | Loose - producer doesn't wait |
| **Resilience**  | Fails if downstream is down        | Survives outages              |
| **Latency**     | Blocked waiting for response       | Fire-and-forget               |
| **Scalability** | Each call = network roundtrip      | Events consumed in batch      |

### Kafka Topics

| Topic                | Events                                         | Consumers                               |
| -------------------- | ---------------------------------------------- | --------------------------------------- |
| `application-events` | `APPLICATION_CREATED`, `APPLICATION_WITHDRAWN` | job-service, notification-service       |
| `application-status` | `STATUS_CHANGED`                               | notification-service, analytics-service |

## Data Model

### Application Entity

```java
{
  "id": "string (MongoDB ObjectId)",
  "username": "string (from JWT sub)",
  "jobId": "string (UUID)",
  "cvId": "string",
  "status": "APPLIED | REVIEWING | INTERVIEW | OFFER | HIRED | REJECTED",
  "note": "string (optional)",
  "jobTitle": "string",
  "companyName": "string",
  "createdAt": "timestamp",
  "updatedAt": "timestamp"
}
```

### Status Flow

```
APPLIED ──────▶ REVIEWING ──────▶ INTERVIEW ──────▶ OFFER ──────▶ HIRED
    │              │                  │               │
    └──────────────┴──────────────────┴───────────────┴──────────▶ REJECTED
```

- **APPLIED**: Initial state when candidate submits
- **REVIEWING**: HR is reviewing the application
- **INTERVIEW**: Candidate is scheduled for interview
- **OFFER**: Job offer extended to candidate
- **HIRED**: Candidate accepted the offer (terminal)
- **REJECTED**: Application rejected (terminal)

## API Endpoints

### Candidate Endpoints

| Method | Endpoint                            | Permission           | Description              |
| ------ | ----------------------------------- | -------------------- | ------------------------ |
| POST   | `/api/v1/applications`              | `application:create` | Submit application       |
| GET    | `/api/v1/applications/my`           | `application:list`   | Get my applications      |
| GET    | `/api/v1/applications/my/count`     | `application:list`   | Get my application count |
| GET    | `/api/v1/applications/{id}`         | Owner/HR/Admin       | Get application by ID    |
| GET    | `/api/v1/applications/check?jobId=` | `application:read`   | Check if already applied |
| DELETE | `/api/v1/applications/{id}`         | Owner only           | Withdraw application     |

### HR Endpoints

| Method | Endpoint                                   | Permission           | Description          |
| ------ | ------------------------------------------ | -------------------- | -------------------- |
| GET    | `/api/v1/applications/job/{jobId}`         | `application:review` | Get job applications |
| GET    | `/api/v1/applications/job/{jobId}/count`   | `application:review` | Get applicant count  |
| PUT    | `/api/v1/applications/{id}/status?status=` | HR/Admin             | Update status        |

## Security

### Authentication

All endpoints require JWT authentication. The username is extracted from the JWT `sub` claim.

```
Authorization: Bearer <JWT_TOKEN>
```

### Permissions

| Permission           | Description              | Roles                |
| -------------------- | ------------------------ | -------------------- |
| `application:create` | Create new applications  | CANDIDATE            |
| `application:list`   | List own applications    | CANDIDATE            |
| `application:read`   | Read application details | CANDIDATE, HR, ADMIN |
| `application:review` | Review job applications  | HR, ADMIN            |
| `application:manage` | Full CRUD access         | ADMIN                |

### Authorization Checks

- **Owner check**: Only the applicant can withdraw their application
- **Status update**: Only HR/Admin can update application status
- **View access**: Candidates can only view their own applications

## Event Payloads

### APPLICATION_CREATED

```json
{
  "eventId": "uuid",
  "eventType": "APPLICATION_CREATED",
  "timestamp": "2024-01-15T10:30:00Z",
  "data": {
    "applicationId": "app_xyz789",
    "username": "john.doe",
    "jobId": "job_456",
    "cvId": "cv_789",
    "note": "Interested in this role"
  }
}
```

### STATUS_CHANGED

```json
{
  "eventId": "uuid",
  "eventType": "STATUS_CHANGED",
  "timestamp": "2024-01-15T14:00:00Z",
  "data": {
    "applicationId": "app_xyz789",
    "username": "john.doe",
    "jobId": "job_456",
    "previousStatus": "APPLIED",
    "newStatus": "REVIEWING",
    "changedBy": "hr.manager",
    "changedAt": "2024-01-15T14:00:00Z"
  }
}
```

### APPLICATION_WITHDRAWN

```json
{
  "eventId": "uuid",
  "eventType": "APPLICATION_WITHDRAWN",
  "timestamp": "2024-01-15T16:00:00Z",
  "data": {
    "applicationId": "app_xyz789",
    "username": "john.doe",
    "jobId": "job_456",
    "withdrawnAt": "2024-01-15T16:00:00Z"
  }
}
```

## Configuration

### application.yml

```yaml
app:
  kafka:
    topics:
      application-events: application-events
      application-status: application-status

spring:
  data:
    mongodb:
      uri: ${MONGO_URI:mongodb://localhost:27019/application_db}
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
```

### Database Indexes

MongoDB indexes for optimal query performance:

```javascript
// Compound index for duplicate check
{ username: 1, jobId: 1 } // unique

// Index for user's applications
{ username: 1, createdAt: -1 }

// Index for job's applicants
{ jobId: 1, createdAt: -1 }

// Index for status filtering
{ status: 1 }
```

## Development

### Running Locally

```bash
# Start infrastructure
./dev.sh infra up

# Start the service
docker-compose up -d --build application-service

# View logs
docker-compose logs -f application-service
```

### Service URLs

| Environment    | URL                                       |
| -------------- | ----------------------------------------- |
| Local (Docker) | http://localhost:9084                     |
| Via Gateway    | http://localhost:9085/api/v1/applications |
| Swagger UI     | http://localhost:9084/swagger-ui.html     |

### Testing

```bash
# Run tests
cd services/application-service
./mvnw test

# Run with Testcontainers
./mvnw verify
```

## Project Structure

```
src/main/java/org/workfitai/applicationservice/
├── config/           # Spring configuration
├── constants/        # Message constants
├── controller/       # REST endpoints
├── dto/
│   ├── kafka/        # Kafka event DTOs
│   ├── request/      # Request DTOs
│   └── response/     # Response DTOs
├── exception/        # Custom exceptions
├── mapper/           # MapStruct mappers
├── messaging/        # Kafka producer
├── model/            # MongoDB entities
├── repository/       # Spring Data repositories
├── security/         # Authorization helpers
└── service/          # Business logic
```

## Error Handling

| Status | Error            | Description              |
| ------ | ---------------- | ------------------------ |
| 400    | Validation Error | Invalid request data     |
| 401    | Unauthorized     | Missing or invalid JWT   |
| 403    | Forbidden        | Insufficient permissions |
| 404    | Not Found        | Application not found    |
| 409    | Conflict         | Duplicate application    |

## Monitoring

### Health Check

```
GET /actuator/health
```

### Metrics

Prometheus metrics available at `/actuator/prometheus`:

- `application_created_total` - Total applications created
- `application_status_changed_total` - Status changes by status type
- `kafka_producer_record_send_total` - Kafka events sent
