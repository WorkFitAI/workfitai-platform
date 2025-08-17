# workfitai-platform

## Observability (Prometheus + Grafana)

- Prometheus UI: http://localhost:9090
- Grafana UI: http://localhost:3001

### Datasource
Grafana → Connections → Prometheus → URL: `http://prometheus:9090`

### Scrape targets (inside Docker network)
- `api-gateway:9005`, `auth-service:9005`
- `application-service:8080`, `monitoring-service:8080`, `job-service:8080`, `user-service:8080`, `cv-service:8080`

### Verify endpoints
```bash
curl -s http://api-gateway:9005/actuator/prometheus | head
for s in application-service monitoring-service job-service user-service cv-service; do
  curl -s http://$s:8080/actuator/prometheus | head
done
