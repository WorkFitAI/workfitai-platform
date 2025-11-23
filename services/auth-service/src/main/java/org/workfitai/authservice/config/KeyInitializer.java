package org.workfitai.authservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.workfitai.authservice.utils.KeyGenerator;

import jakarta.annotation.PostConstruct;

/**
 * Auto-generates RSA keys BEFORE Spring configuration loading
 * This ensures keys exist when RsaKeyProperties tries to load them
 */
@Component
public class KeyInitializer {

    private static final Logger logger = LoggerFactory.getLogger(KeyInitializer.class);

    // Static block ensures keys are generated VERY early in application lifecycle
    static {
        try {
            KeyGenerator.generateKeysIfNotExist();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize RSA keys during class loading", e);
        }
    }

    @PostConstruct
    public void init() {
        logger.info("🔑 RSA keys are ready for JWT authentication");
    }
}