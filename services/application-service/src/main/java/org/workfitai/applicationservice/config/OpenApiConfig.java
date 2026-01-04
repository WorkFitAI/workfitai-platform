package org.workfitai.applicationservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3.0 (Swagger) configuration for application-service.
 * 
 * Provides:
 * - API documentation at /v3/api-docs
 * - Interactive UI at /swagger-ui.html
 * - JWT Bearer authentication support
 * 
 * Access in development:
 * - Local: http://localhost:9084/swagger-ui.html
 * - Via Gateway: http://localhost:9085/application/swagger-ui.html
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:9084}")
    private int serverPort;

    /**
     * Configures OpenAPI documentation.
     * 
     * Features:
     * - Bearer token authentication
     * - API versioning info
     * - Contact information
     * 
     * @return Configured OpenAPI object
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Local development server"),
                        new Server()
                                .url("http://localhost:9085")
                                .description("API Gateway")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", securityScheme()))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    /**
     * API information displayed in Swagger UI.
     */
    private Info apiInfo() {
        return new Info()
                .title("WorkFitAI Application Service API")
                .description("""
                        Job Application Management API

                        ## Features
                        - Submit job applications with CV
                        - View application history
                        - Track application status
                        - HR workflow for reviewing applicants

                        ## Authentication
                        All endpoints require JWT Bearer token (except /check).
                        Obtain token from auth-service POST /api/v1/auth/login.

                        ## Roles
                        - **CANDIDATE**: Submit applications, view own applications
                        - **HR**: View applicants, update application status
                        - **ADMIN**: Full access
                        """)
                .version("1.0.0")
                .contact(new Contact()
                        .name("WorkFitAI Team")
                        .email("support@workfitai.org"))
                .license(new License()
                        .name("MIT License")
                        .url("https://opensource.org/licenses/MIT"));
    }

    /**
     * JWT Bearer authentication scheme.
     * 
     * Users can click "Authorize" in Swagger UI,
     * enter their JWT token, and test authenticated endpoints.
     */
    private SecurityScheme securityScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Enter JWT token obtained from auth-service");
    }
}
