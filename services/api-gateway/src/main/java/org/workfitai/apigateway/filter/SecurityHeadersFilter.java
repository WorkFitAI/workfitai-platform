package org.workfitai.apigateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Security Headers Filter
 * 
 * Adds security headers to all responses to protect against common
 * vulnerabilities:
 * - XSS (Cross-Site Scripting)
 * - Clickjacking
 * - MIME sniffing
 * - Information disclosure
 * 
 * Headers added:
 * - X-Frame-Options: Prevents clickjacking attacks
 * - X-Content-Type-Options: Prevents MIME sniffing
 * - X-XSS-Protection: Enables browser XSS protection
 * - Referrer-Policy: Controls referrer information
 * - Content-Security-Policy: Defines trusted content sources
 * - Strict-Transport-Security: Enforces HTTPS (only for HTTPS requests)
 */
@Component
@Slf4j
public class SecurityHeadersFilter implements GlobalFilter, Ordered {

    @Override
    public int getOrder() {
        return -50; // Run early
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Add security headers BEFORE response is committed
        exchange.getResponse().beforeCommit(() -> {
            HttpHeaders headers = exchange.getResponse().getHeaders();

            // Prevent clickjacking - deny embedding in iframes
            headers.add("X-Frame-Options", "DENY");

            // Prevent MIME sniffing - force declared content type
            headers.add("X-Content-Type-Options", "nosniff");

            // Enable XSS protection in browsers
            headers.add("X-XSS-Protection", "1; mode=block");

            // Referrer policy - control referrer information sent
            headers.add("Referrer-Policy", "strict-origin-when-cross-origin");

            // Content Security Policy - restrict resource loading
            // Adjust based on your frontend needs
            headers.add("Content-Security-Policy",
                    "default-src 'self'; " +
                            "script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
                            "style-src 'self' 'unsafe-inline'; " +
                            "img-src 'self' data: https: blob:; " +
                            "font-src 'self' data:; " +
                            "connect-src 'self' ws: wss:; " +
                            "frame-ancestors 'none';");

            // HSTS (HTTP Strict Transport Security) - only for HTTPS
            if ("https".equals(exchange.getRequest().getURI().getScheme())) {
                headers.add("Strict-Transport-Security",
                        "max-age=31536000; includeSubDomains; preload");
                log.debug("✅ HSTS header added (HTTPS detected)");
            }

            // Permissions Policy - restrict browser features
            headers.add("Permissions-Policy",
                    "geolocation=(), microphone=(), camera=()");

            // Remove server header to prevent information disclosure
            headers.remove("Server");
            headers.remove("X-Powered-By");

            log.debug("🔒 Security headers added to response for: {}",
                    exchange.getRequest().getPath().value());

            return Mono.empty();
        });

        return chain.filter(exchange);
    }
}
