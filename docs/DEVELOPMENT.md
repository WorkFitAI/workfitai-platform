# =============================================================================
# WorkFitAI Platform - Development Guide
# =============================================================================

## 🚀 Quick Start

### Development Script (Recommended)
```bash
# Start everything with auto-build
./dev.sh full up

# Start only infrastructure
./dev.sh infra up

# Restart with rebuild
./dev.sh full restart

# Stop everything
./dev.sh full stop

# View logs
./dev.sh full logs
./dev.sh full logs monitoring-service

# Clean everything
./dev.sh full clean
```

### Manual Docker Compose Commands
```bash
# Always rebuild and start
docker-compose --profile full up --build -d

# Start only infrastructure
docker-compose --profile infra up --build -d

# Stop everything
docker-compose --profile full down

# Force rebuild without cache
docker-compose --profile full build --no-cache
docker-compose --profile full up -d
```

## 📊 Service Endpoints

### Infrastructure Services
- **Consul UI**: http://localhost:8500
- **Vault UI**: http://localhost:8200 (Token: `dev-token`)
- **Kafka UI**: http://localhost:8080
- **Grafana**: http://localhost:3001 (admin/admin)
- **Prometheus**: http://localhost:9090

### Application Services
- **Auth Service**: http://localhost:9080
- **User Service**: http://localhost:9081
- **Job Service**: http://localhost:9082
- **CV Service**: http://localhost:9083
- **Application Service**: http://localhost:9084
- **API Gateway**: http://localhost:9085
- **Monitoring Service**: http://localhost:9086

## 🔧 Vault Management

### Check Vault Status
```bash
curl http://localhost:9086/api/vault/status
```

### Reinitialize Vault Secrets
```bash
curl -X POST http://localhost:9086/api/vault/reinitialize
```

### View Service Configuration
```bash
curl http://localhost:9086/api/vault/config/auth-service
```

## 🐛 Troubleshooting

### View Service Logs
```bash
# All services
./dev.sh full logs

# Specific service
./dev.sh full logs monitoring-service
docker-compose logs -f monitoring-service
```

### Rebuild Specific Service
```bash
docker-compose build monitoring-service --no-cache
docker-compose up monitoring-service -d
```

### Clean Start
```bash
./dev.sh full clean
./dev.sh full up
```

## 📁 Project Structure

```
workfitai-platform/
├── services/
│   ├── auth-service/
│   ├── user-service/
│   ├── job-service/
│   ├── cv-service/
│   ├── application-service/
│   ├── api-gateway/
│   └── monitoring-service/    # Vault initialization & monitoring
├── vault/                     # Vault configuration
├── grafana/                   # Grafana dashboards
├── initialize/                # Initialization scripts
├── docker-compose.yml         # Main composition
└── dev.sh                     # Development helper script
```

## 🔑 Environment Variables

Key variables in `.env.local`:
- `VAULT_TOKEN=dev-token`
- `SPRING_PROFILES_ACTIVE=local`
- Database connection strings
- Service URLs

## 🏗️ Development Workflow

1. **Make code changes**
2. **Restart with rebuild**: `./dev.sh full restart`
3. **Check logs**: `./dev.sh full logs [service-name]`
4. **Access services** via endpoints above

## ⚠️ Important Notes

- Services will auto-rebuild when using `./dev.sh` or `--build` flag
- Vault secrets are automatically initialized by monitoring-service
- Use `dev-token` to access Vault UI
- All services use `docker` Spring profile in containers

## 📧 Email Configuration (Notification Service)

The notification-service requires SMTP configuration to send application confirmation and HR notification emails.

### Configuration File
Edit `services/notification-service/src/main/resources/application.yml`:

```yaml
spring:
  mail:
    host: smtp.gmail.com          # SMTP server
    port: 587                      # SMTP port (587 for TLS)
    username: ${MAIL_USERNAME:}    # Environment variable or leave empty
    password: ${MAIL_PASSWORD:}    # Environment variable or leave empty
    properties:
      mail:
        smtp:
          auth: true               # Enable SMTP authentication
          starttls:
            enable: true           # Enable TLS encryption
```

### Gmail Setup (Development)

1. **Enable 2-Factor Authentication**:
   - Go to https://myaccount.google.com/security
   - Enable 2-Step Verification

2. **Generate App Password**:
   - Go to https://myaccount.google.com/apppasswords
   - Select "Mail" and "Other (Custom name)"
   - Enter "WorkFitAI Notification Service"
   - Copy the 16-character password

3. **Set Environment Variables**:
   ```bash
   # Linux/Mac
   export MAIL_USERNAME=your-email@gmail.com
   export MAIL_PASSWORD=your-app-password

   # Windows PowerShell
   $env:MAIL_USERNAME="your-email@gmail.com"
   $env:MAIL_PASSWORD="your-app-password"
   ```

4. **Or Update application.yml directly** (not recommended for production):
   ```yaml
   spring:
     mail:
       username: your-email@gmail.com
       password: your-app-password
   ```

### Alternative SMTP Providers

#### SendGrid
```yaml
spring:
  mail:
    host: smtp.sendgrid.net
    port: 587
    username: apikey
    password: ${SENDGRID_API_KEY}
```

#### AWS SES
```yaml
spring:
  mail:
    host: email-smtp.us-east-1.amazonaws.com
    port: 587
    username: ${AWS_SES_USERNAME}
    password: ${AWS_SES_PASSWORD}
```

#### Custom SMTP
```yaml
spring:
  mail:
    host: mail.yourdomain.com
    port: 587
    username: noreply@yourdomain.com
    password: ${SMTP_PASSWORD}
```

### Email Templates

Email templates are located in `services/notification-service/src/main/resources/templates/`:

- **application-confirmation.html**: Sent to candidates upon application submission
- **new-application-hr.html**: Sent to HR when new application is received

Template variables:
- Candidate email: `candidateName`, `jobTitle`, `companyName`, `applicationId`, `appliedAt`

### Graceful Degradation

If email credentials are not configured:
- Application submission will still succeed
- Kafka events will be published
- Email sending will be skipped with warning logs
- EmailLog entries will not be created

This allows development without SMTP configuration.

### Testing Email Functionality

1. **Configure SMTP credentials** (see above)

2. **Start notification-service**:
   ```bash
   cd services/notification-service
   ./mvnw spring-boot:run
   ```

3. **Submit a job application**:
   ```bash
   curl -X POST http://localhost:8088/api/v1/applications \
     -H "Authorization: Bearer {jwt-token}" \
     -F "cvFile=@resume.pdf" \
     -F "coverLetter=I am interested..." \
     -F "jobId=550e8400-e29b-41d4-a716-446655440000"
   ```

4. **Check logs**:
   ```bash
   # notification-service logs
   docker logs notification-service -f

   # Expected output:
   [INFO] Received ApplicationCreatedEvent: 64a5f8e3...
   [INFO] Fetched candidate email: candidate@example.com
   [INFO] Sent candidate confirmation email
   [INFO] Fetched HR email: hr@techcorp.com
   [INFO] Sent HR notification email
   [INFO] Saved email logs to MongoDB
   ```

5. **Verify emails in inbox**:
   - Candidate should receive "Application Submitted: {jobTitle}"
   - HR should receive "New Application: {jobTitle}"

6. **Check email logs in MongoDB**:
   ```bash
   docker exec -it mongodb mongosh
   use notification_db
   db.email_logs.find({ applicationId: "64a5f8e3c9b2d1a3e4f5b6c7" }).pretty()
   ```

### Troubleshooting Email Issues

**Issue**: Emails not sent

**Solutions**:
1. Check SMTP credentials are correct
2. Verify Gmail App Password (not regular password)
3. Check notification-service logs for errors
4. Verify Kafka consumer is running: `docker logs notification-service | grep "Received ApplicationCreatedEvent"`
5. Test SMTP connection:
   ```bash
   telnet smtp.gmail.com 587
   ```

**Issue**: "Authentication failed" error

**Solutions**:
1. Regenerate Gmail App Password
2. Enable "Less secure app access" (not recommended)
3. Check 2FA is enabled on Gmail account

**Issue**: Emails sent but not received

**Solutions**:
1. Check spam/junk folder
2. Verify recipient email is correct in user-service
3. Check SendGrid/Gmail sending limits not exceeded
4. Review email logs in MongoDB for error messages
