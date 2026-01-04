package org.workfitai.applicationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main entry point for application-service.
 *
 * Annotations:
 * - @SpringBootApplication: Standard Spring Boot auto-configuration
 * - @EnableDiscoveryClient: Enables Consul service discovery
 * - @EnableFeignClients: Enables Feign clients for cross-service calls
 * - @EnableMongoAuditing: Enables @CreatedDate, @LastModifiedDate on entities
 * - @EnableAspectJAutoProxy: Enables AOP for audit logging
 * - @EnableAsync: Enables async method execution (@Async)
 * - @EnableCaching: Enables Spring Cache abstraction (@Cacheable)
 *
 * Dependencies:
 * - MongoDB: Stores application documents
 * - Consul: Service discovery for Feign clients
 * - Auth-service: JWT public key for authentication
 * - Job-service: Validates jobs and gets job details
 * - CV-service: Validates CVs and ownership
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "org.workfitai.applicationservice.client")
@EnableMongoAuditing
@EnableAspectJAutoProxy
@EnableAsync
@EnableCaching
public class ApplicationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApplicationServiceApplication.class, args);
    }
}
