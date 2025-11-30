package org.workfitai.applicationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/**
 * Main entry point for application-service.
 * 
 * Annotations:
 * - @SpringBootApplication: Standard Spring Boot auto-configuration
 * - @EnableDiscoveryClient: Enables Consul service discovery
 * - @EnableFeignClients: Enables Feign clients for cross-service calls
 * - @EnableMongoAuditing: Enables @CreatedDate, @LastModifiedDate on entities
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
@EnableFeignClients
@EnableMongoAuditing
public class ApplicationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApplicationServiceApplication.class, args);
    }
}
