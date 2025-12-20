# Vault Integration Guide - Recommendation Engine

## Tổng Quan

Recommendation Engine đã được tích hợp với HashiCorp Vault để quản lý tập trung các biến môi trường và secrets.

## Cấu Trúc Vault

### Secrets Path
```
secret/
└── recommendation-engine/
    ├── model.path
    ├── model.dimension
    ├── batch.size
    ├── kafka.bootstrap.servers
    ├── kafka.consumer.group
    ├── job.service.url
    └── ... (35+ configuration keys)
```

### Policy
Service có policy riêng: `recommendation-engine-policy` chỉ cho phép đọc secrets từ path `secret/data/recommendation-engine`

## Cách Hoạt Động

### 1. Khởi Động
```
1. vault-init service chạy → Tạo secrets trong Vault
2. recommendation-engine khởi động
3. vault_client.py load secrets từ Vault
4. config.py merge với environment variables
5. Service sử dụng config đã merge
```

### 2. Ưu Tiên Cấu Hình
```
Environment Variables > Vault Secrets > Default Values
```

Nếu một biến đã có trong environment, Vault sẽ không ghi đè.

## Cấu Hình

### Environment Variables Bắt Buộc

```bash
# docker-compose.yml
environment:
  - VAULT_ENABLED=true              # Bật/tắt Vault integration
  - VAULT_ADDR=http://vault:8200    # Vault server address
  - VAULT_TOKEN=dev-token           # Vault authentication token
```

### Disable Vault (Development)

Nếu muốn chạy service mà không dùng Vault:

```bash
# docker-compose.yml
environment:
  - VAULT_ENABLED=false
  # Sau đó cần cung cấp tất cả các biến trực tiếp
  - MODEL_PATH=/app/models/bi-encoder-e5-large
  - KAFKA_BOOTSTRAP_SERVERS=kafka:29092
  # ... etc
```

## Quản Lý Secrets

### Xem Secrets Hiện Tại

```bash
# Từ máy host
docker exec -it vault sh

# Login
export VAULT_ADDR=http://localhost:8200
export VAULT_TOKEN=dev-token

# Đọc secrets
vault kv get secret/recommendation-engine

# Đọc một key cụ thể
vault kv get -field=kafka.bootstrap.servers secret/recommendation-engine
```

### Cập Nhật Secrets

```bash
# Cập nhật một key
vault kv patch secret/recommendation-engine \
  search.default.top-k=30

# Cập nhật nhiều keys
vault kv patch secret/recommendation-engine \
  kafka.bootstrap.servers=kafka:29092 \
  job.service.url=http://job-service:9082
```

### Thêm Secret Mới

1. **Update .env.local**
```bash
# .env.local
RECOMMENDATION_NEW_SETTING=value
```

2. **Update vault-init script**
```bash
# scripts/init-vault.sh
create_service_secrets "recommendation-engine" '{
  "data": {
    ...
    "new.setting": "'"${RECOMMENDATION_NEW_SETTING:-default}"'"
  }
}'
```

3. **Update vault_client.py**
```python
# app/vault_client.py
config_mapping = {
    ...
    "NEW_SETTING": secrets.get("new.setting"),
}
```

4. **Restart services**
```bash
docker-compose down vault-init recommendation-engine
docker-compose up -d vault-init recommendation-engine
```

## Troubleshooting

### Service không kết nối được Vault

**Kiểm tra:**
```bash
# 1. Vault đã chạy chưa?
docker ps | grep vault

# 2. Health check
curl http://localhost:8200/v1/sys/health

# 3. Token đúng chưa?
docker exec vault vault token lookup $VAULT_TOKEN

# 4. Logs của recommendation-engine
docker logs recommendation-engine | grep -i vault
```

**Log thành công:**
```
INFO - ✓ Connected to Vault at http://vault:8200
INFO - ✓ Loaded 35 secrets from Vault path: recommendation-engine
INFO - ✓ Loaded 35 configuration values from Vault
```

### Secrets không cập nhật

Vault cache secrets khi startup. Để refresh:

```bash
# 1. Cập nhật secrets trong Vault
vault kv patch secret/recommendation-engine key=new-value

# 2. Restart service
docker restart recommendation-engine
```

### Permission Denied

Kiểm tra policy:
```bash
# Xem policy
vault policy read recommendation-engine-policy

# Policy đúng phải cho phép:
path "secret/data/recommendation-engine" {
  capabilities = ["read"]
}
```

## Production Best Practices

### 1. Riêng Token Cho Mỗi Service

Thay vì dùng chung `dev-token`:

```bash
# Tạo token riêng
vault token create \
  -policy=recommendation-engine-policy \
  -display-name=recommendation-engine \
  -ttl=720h

# Dùng token mới trong docker-compose
environment:
  - VAULT_TOKEN=s.xxxxxxxxxxxxx
```

### 2. Rotate Secrets Định Kỳ

```bash
# Script rotate
vault kv patch secret/recommendation-engine \
  kafka.password=new-secure-password

# Restart để áp dụng
docker restart recommendation-engine
```

### 3. Audit Logging

Enable Vault audit:
```bash
vault audit enable file file_path=/vault/logs/audit.log
```

### 4. Backup Secrets

```bash
# Export tất cả secrets
vault kv get -format=json secret/recommendation-engine > backup.json

# Restore
vault kv put secret/recommendation-engine @backup.json
```

## Environment Variables Reference

### Được Lưu Trong Vault

Tất cả các biến sau được quản lý qua Vault:

```bash
model.path                    # Model directory path
model.dimension               # Embedding dimension (1024)
batch.size                    # Batch size for embeddings
kafka.bootstrap.servers       # Kafka broker addresses
kafka.consumer.group          # Consumer group ID
kafka.topic.job-created       # Topic names
kafka.topic.job-updated
kafka.topic.job-deleted
job.service.url               # Job Service endpoint
search.default.top-k          # Default number of results
cache.enable                  # Enable caching
metrics.enable                # Enable Prometheus metrics
# ... và 25+ keys khác
```

### Override Qua Environment (docker-compose)

Chỉ những biến quan trọng hoặc thay đổi theo môi trường:

```bash
VAULT_ENABLED=true            # Bật Vault
VAULT_ADDR=http://vault:8200  # Vault address
VAULT_TOKEN=dev-token         # Auth token
MODEL_PATH=/app/models/...    # Container-specific path
FAISS_INDEX_PATH=/app/data/...# Container-specific path
```

## Security Checklist

- [ ] Sử dụng token riêng cho mỗi service (không dùng root token)
- [ ] Rotate tokens định kỳ (TTL < 30 days)
- [ ] Enable audit logging
- [ ] Backup secrets hàng tuần
- [ ] Restrict network access đến Vault (không expose port 8200 ra public)
- [ ] Sử dụng TLS trong production
- [ ] Review và minimize policy permissions

## Example Workflow

### Development
```bash
# 1. Start infrastructure
./dev.sh infra up

# 2. Verify Vault
curl http://localhost:8200/v1/sys/health

# 3. Check secrets
vault kv get secret/recommendation-engine

# 4. Start service
./dev.sh full up recommendation-engine

# 5. Verify config loaded
curl http://localhost:8001/health
```

### Update Configuration
```bash
# 1. Update Vault
vault kv patch secret/recommendation-engine \
  search.default.top-k=50

# 2. Restart service
docker restart recommendation-engine

# 3. Verify
curl http://localhost:8001/health | jq .
```
