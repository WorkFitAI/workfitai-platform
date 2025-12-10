package org.workfitai.monitoringservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.workfitai.monitoringservice.service.ConfigurationService;

import java.util.HashMap;
import java.util.Map;

@Component
public class VaultInitializer implements CommandLineRunner {

        private static final Logger logger = LoggerFactory.getLogger(VaultInitializer.class);

        private final ConfigurationService configurationService;
        private final Environment environment;

        @Autowired
        public VaultInitializer(ConfigurationService configurationService, Environment environment) {
                this.configurationService = configurationService;
                this.environment = environment;
        }

        @Override
        public void run(String... args) throws Exception {
                logger.info("Starting Vault secrets initialization...");

                try {
                        // Wait for Vault to be ready
                        Thread.sleep(5000);

                        initializeAllServiceSecrets();

                        logger.info("Vault secrets initialization completed successfully!");
                } catch (Exception e) {
                        logger.error("Failed to initialize Vault secrets", e);
                }
        }

        public void initializeAllServiceSecrets() {
                // Initialize auth-service secrets
                Map<String, Object> authConfig = new HashMap<>();
                authConfig.put("jwt.access.expiration", environment.getProperty("AUTH_JWT_ACCESS_EXP_MS", "900000"));
                authConfig.put("jwt.refresh.expiration",
                                environment.getProperty("AUTH_JWT_REFRESH_EXP_MS", "604800000"));
                authConfig.put("mongodb.uri",
                                environment.getProperty("SPRING_DATA_MONGODB_URI",
                                                "mongodb://auth-mongo:27017/auth-db"));
                authConfig.put("redis.host", environment.getProperty("REDIS_AUTH_HOST", "auth-redis"));
                authConfig.put("redis.port", environment.getProperty("REDIS_AUTH_PORT", "6379"));

                // Password Policy
                authConfig.put("password.policy.min-length", "8");
                authConfig.put("password.policy.require-uppercase", "true");
                authConfig.put("password.policy.require-lowercase", "true");
                authConfig.put("password.policy.require-digit", "true");
                authConfig.put("password.policy.require-special-char", "true");
                authConfig.put("password.policy.special-chars", "@#$%^&+=!*()_-");
                authConfig.put("password.policy.max-age-days", "90");
                authConfig.put("password.policy.history-count", "5");
                authConfig.put("password.policy.change-rate-limit", "3");
                authConfig.put("password.policy.change-rate-window", "3600");
                authConfig.put("password.policy.forgot-rate-limit", "5");
                authConfig.put("password.policy.forgot-rate-window", "86400");

                // Session Management
                authConfig.put("session.max-concurrent-sessions", "5");
                authConfig.put("session.timeout", "3600");
                authConfig.put("session.refresh-token-validity", "604800");
                authConfig.put("session.enable-device-tracking", "true");
                authConfig.put("session.enable-location-tracking", "true");
                authConfig.put("session.require-2fa-for-new-device", "false");

                // Two-Factor Authentication
                authConfig.put("two-factor.issuer", "WorkFitAI");
                authConfig.put("two-factor.totp-window", "1");
                authConfig.put("two-factor.totp-period", "30");
                authConfig.put("two-factor.backup-codes-count", "10");
                authConfig.put("two-factor.qr-code-size", "300");
                authConfig.put("two-factor.email-otp-length", "6");
                authConfig.put("two-factor.email-otp-validity", "600");

                configurationService.initializeServiceConfig("auth", authConfig);

                // Initialize user-service secrets
                Map<String, Object> userConfig = new HashMap<>();
                userConfig.put("datasource.url",
                                "jdbc:postgresql://" + environment.getProperty("USER_HOST", "user-postgres") + ":"
                                                + environment.getProperty("USER_DB_PORT", "5432") + "/"
                                                + environment.getProperty("USER_DB_NAME", "user_db"));
                userConfig.put("datasource.username", environment.getProperty("USER_DB_USER", "user"));
                userConfig.put("datasource.password", environment.getProperty("USER_DB_PASS", "pass"));
                userConfig.put("redis.host", environment.getProperty("REDIS_AUTH_HOST", "auth-redis"));
                userConfig.put("redis.port", environment.getProperty("REDIS_AUTH_PORT", "6379"));
                userConfig.put("kafka.bootstrap-servers",
                                environment.getProperty("KAFKA_BOOTSTRAP_SERVERS", "kafka:29092"));

                // Cloudinary Configuration
                userConfig.put("cloudinary.cloud-name",
                                environment.getProperty("CLOUDINARY_CLOUD_NAME", "workfitai-dev"));
                userConfig.put("cloudinary.api-key", environment.getProperty("CLOUDINARY_API_KEY", ""));
                userConfig.put("cloudinary.api-secret", environment.getProperty("CLOUDINARY_API_SECRET", ""));
                userConfig.put("cloudinary.upload-preset", "user_avatars");
                userConfig.put("cloudinary.folder", "avatars");
                userConfig.put("cloudinary.max-file-size", "5242880");
                userConfig.put("cloudinary.allowed-formats", "jpg,jpeg,png,webp");
                userConfig.put("cloudinary.transformation.width", "400");
                userConfig.put("cloudinary.transformation.height", "400");
                userConfig.put("cloudinary.transformation.crop", "fill");
                userConfig.put("cloudinary.transformation.gravity", "face");
                userConfig.put("cloudinary.transformation.quality", "auto");

                // Account Management
                userConfig.put("account.deactivation-retention-days", "30");
                userConfig.put("account.deletion-grace-period-days", "7");
                userConfig.put("account.enable-auto-cleanup", "true");
                userConfig.put("account.cleanup-cron", "0 0 2 * * ?");
                userConfig.put("account.notify-before-deletion-days", "3,1");

                configurationService.initializeServiceConfig("user", userConfig);

                // Initialize api-gateway secrets
                Map<String, Object> gatewayConfig = new HashMap<>();
                gatewayConfig.put("auth.service.url",
                                environment.getProperty("AUTH_SERVICE_URL", "http://auth-service:9005"));
                gatewayConfig.put("redis.host", environment.getProperty("REDIS_GATEWAY_HOST", "api-redis"));
                gatewayConfig.put("redis.port", environment.getProperty("REDIS_GATEWAY_PORT", "6379"));
                configurationService.initializeServiceConfig("api-gateway", gatewayConfig);

                // Initialize job-service secrets
                Map<String, Object> jobConfig = new HashMap<>();
                jobConfig.put("datasource.url",
                                "jdbc:postgresql://" + environment.getProperty("JOB_HOST", "job-postgres") + ":"
                                                + environment.getProperty("JOB_DB_PORT", "5432") + "/"
                                                + environment.getProperty("JOB_DB_NAME", "job_db"));
                jobConfig.put("datasource.username", environment.getProperty("JOB_DB_USER", "user"));
                jobConfig.put("datasource.password", environment.getProperty("JOB_DB_PASS", "job@123"));
                jobConfig.put("kafka.bootstrap-servers",
                                environment.getProperty("KAFKA_BOOTSTRAP_SERVERS", "kafka:29092"));
                configurationService.initializeServiceConfig("job", jobConfig);

                // Initialize cv-service secrets
                Map<String, Object> cvConfig = new HashMap<>();
                cvConfig.put("mongodb.host", environment.getProperty("CV_HOST", "cv-mongo"));
                cvConfig.put("mongodb.port", environment.getProperty("CV_DB_PORT", "27017"));
                cvConfig.put("mongodb.database", environment.getProperty("CV_DB_NAME", "cv-db"));
                cvConfig.put("mongodb.username", environment.getProperty("CV_DB_USER", "user"));
                cvConfig.put("mongodb.password", environment.getProperty("CV_DB_PASS", "123456"));
                cvConfig.put("mongodb.auth-database", environment.getProperty("CV_DB_NAME", "cv-db"));
                cvConfig.put("redis.host", environment.getProperty("REDIS_AUTH_HOST", "cv-redis"));
                cvConfig.put("redis.port", "6379");
                configurationService.initializeServiceConfig("cv", cvConfig);

                // Initialize application-service secrets
                Map<String, Object> appConfig = new HashMap<>();
                appConfig.put("mongodb.uri", "mongodb://application-mongo:27017/app-db");
                appConfig.put("kafka.bootstrap-servers",
                                environment.getProperty("KAFKA_BOOTSTRAP_SERVERS", "kafka:29092"));
                configurationService.initializeServiceConfig("application-service", appConfig);

                // Initialize monitoring-service secrets (self)
                Map<String, Object> monitoringConfig = new HashMap<>();
                monitoringConfig.put("redis.host", environment.getProperty("REDIS_GATEWAY_HOST", "api-redis"));
                monitoringConfig.put("redis.port", environment.getProperty("REDIS_GATEWAY_PORT", "6379"));
                monitoringConfig.put("elasticsearch.host",
                                environment.getProperty("ELASTICSEARCH_HOST", "elasticsearch"));
                monitoringConfig.put("elasticsearch.port", environment.getProperty("ELASTICSEARCH_PORT", "9200"));
                monitoringConfig.put("elasticsearch.username",
                                environment.getProperty("ELASTICSEARCH_USERNAME", "elastic"));
                monitoringConfig.put("elasticsearch.password",
                                environment.getProperty("ELASTICSEARCH_PASSWORD", "workfitai123"));
                monitoringConfig.put("kibana.host", environment.getProperty("KIBANA_HOST", "kibana"));
                monitoringConfig.put("kibana.port", environment.getProperty("KIBANA_PORT", "5601"));
                monitoringConfig.put("kibana.system.password",
                                environment.getProperty("KIBANA_SYSTEM_PASSWORD", "workfitai123"));
                configurationService.initializeServiceConfig("monitoring-service", monitoringConfig);

                // Initialize notification-service secrets
                Map<String, Object> notificationConfig = new HashMap<>();
                notificationConfig.put("spring.mail.host",
                                environment.getProperty("EMAIL_SMTP_HOST", "smtp.gmail.com"));
                notificationConfig.put("spring.mail.port", environment.getProperty("EMAIL_SMTP_PORT", "587"));
                notificationConfig.put("spring.mail.username", environment.getProperty("EMAIL_ADDRESS", ""));
                notificationConfig.put("spring.mail.password", environment.getProperty("EMAIL_APP_PASSWORD", ""));
                notificationConfig.put("spring.data.mongodb.uri",
                                environment.getProperty("SPRING_DATA_MONGODB_URI",
                                                "mongodb://notif-mongo:27017/notification-db"));
                notificationConfig.put("spring.kafka.bootstrap-servers",
                                environment.getProperty("KAFKA_BOOTSTRAP_SERVERS", "kafka:29092"));
                configurationService.initializeServiceConfig("notification-service", notificationConfig);
        }
}
