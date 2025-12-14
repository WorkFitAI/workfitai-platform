package org.workfitai.monitoringservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.workfitai.monitoringservice.service.ConfigurationService;

/**
 * Validates Vault configuration after service startup.
 * Vault secrets must be initialized by DevOps/Infra layer (vault-init
 * container) before services start.
 * This component only validates that required configuration is loaded
 * correctly.
 */
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
                logger.info("✅ Validating Vault configuration...");

                try {
                        // Validate that monitoring-service config was loaded from Vault
                        var response = configurationService.getServiceConfig("monitoring-service");

                        if (response == null || response.getData() == null || response.getData().isEmpty()) {
                                logger.warn("⚠️  No configuration loaded from Vault for monitoring-service");
                                logger.warn("⚠️  Ensure vault-init container has completed successfully");
                                logger.warn("💡 Run: docker-compose logs vault-init");
                        } else {
                                logger.info("✅ Vault configuration validated successfully");
                                logger.info("📊 Loaded {} configuration properties from Vault",
                                                response.getData().size());
                        }
                } catch (Exception e) {
                        logger.error("❌ Failed to validate Vault configuration", e);
                        logger.error("💡 Check that vault-init container completed before service startup");
                        logger.error("💡 Run: docker-compose ps | grep vault-init");
                }
        }
}
