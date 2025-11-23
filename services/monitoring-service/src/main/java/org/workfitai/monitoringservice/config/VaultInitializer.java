package org.workfitai.monitoringservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.workfitai.monitoringservice.service.ConfigurationService;

import java.util.HashMap;
import java.util.Map;

@Component
public class VaultInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(VaultInitializer.class);

    private final ConfigurationService configurationService;

    @Autowired
    public VaultInitializer(ConfigurationService configurationService) {
        this.configurationService = configurationService;
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
        authConfig.put("jwt.secret", "CJmTxK/y/bJPsho/gIxQofHzv3nj+FoABHTSNsBwqCTy2bN3TavCklalHVGw/6KX");
        authConfig.put("jwt.access.expiration", "900000");
        authConfig.put("jwt.refresh.expiration", "604800000");
        authConfig.put("mongodb.uri", "mongodb://auth-mongo:27017/auth-db");
        authConfig.put("redis.host", "auth-redis");
        authConfig.put("redis.port", "6379");
        configurationService.initializeServiceConfig("auth", authConfig);

        // Initialize user-service secrets
        Map<String, Object> userConfig = new HashMap<>();
        userConfig.put("datasource.url", "jdbc:postgresql://user-postgres:5432/user_db");
        userConfig.put("datasource.username", "user");
        userConfig.put("datasource.password", "pass");
        userConfig.put("redis.host", "auth-redis");
        userConfig.put("redis.port", "6379");
        userConfig.put("kafka.bootstrap-servers", "kafka:29092");
        configurationService.initializeServiceConfig("user", userConfig);

        // Initialize api-gateway secrets
        Map<String, Object> gatewayConfig = new HashMap<>();
        gatewayConfig.put("auth.service.url", "http://auth-service:9005");
        gatewayConfig.put("redis.host", "api-redis");
        gatewayConfig.put("redis.port", "6379");
        configurationService.initializeServiceConfig("api-gateway", gatewayConfig);

        // Initialize job-service secrets
        Map<String, Object> jobConfig = new HashMap<>();
        jobConfig.put("datasource.url", "jdbc:postgresql://job-postgres:5432/job_db");
        jobConfig.put("datasource.username", "user");
        jobConfig.put("datasource.password", "job@123");
        jobConfig.put("kafka.bootstrap-servers", "kafka:29092");
        configurationService.initializeServiceConfig("job", jobConfig);

        // Initialize cv-service secrets
        Map<String, Object> cvConfig = new HashMap<>();
        cvConfig.put("mongodb.host", "cv-mongo");
        cvConfig.put("mongodb.port", "27017");
        cvConfig.put("mongodb.database", "cv-db");
        cvConfig.put("mongodb.username", "user");
        cvConfig.put("mongodb.password", "123456");
        cvConfig.put("mongodb.auth-database", "cv-db");
        configurationService.initializeServiceConfig("cv", cvConfig);

        // Initialize application-service secrets
        Map<String, Object> appConfig = new HashMap<>();
        appConfig.put("mongodb.uri", "mongodb://application-mongo:27017/app-db");
        appConfig.put("kafka.bootstrap-servers", "kafka:29092");
        configurationService.initializeServiceConfig("application-service", appConfig);

        // Initialize monitoring-service secrets (self)
        Map<String, Object> monitoringConfig = new HashMap<>();
        monitoringConfig.put("redis.host", "api-redis");
        monitoringConfig.put("redis.port", "6379");
        configurationService.initializeServiceConfig("monitoring-service", monitoringConfig);
    }
}