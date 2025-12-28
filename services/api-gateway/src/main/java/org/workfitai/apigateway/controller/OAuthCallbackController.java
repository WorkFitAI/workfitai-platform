package org.workfitai.apigateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import org.workfitai.apigateway.service.IOpaqueTokenService;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * OAuth callback endpoint on API Gateway
 * Converts JWT tokens to opaque tokens before redirecting to frontend
 */
@RestController
@RequestMapping("/auth/oauth")
@RequiredArgsConstructor
@Slf4j
public class OAuthCallbackController {

    private final IOpaqueTokenService opaqueTokenService;

    @Value("${app.frontend.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    /**
     * OAuth success endpoint - receives JWT from auth-service, converts to opaque,
     * redirects to frontend
     * 
     * @param accessToken  JWT access token from auth-service
     * @param refreshToken JWT refresh token from auth-service
     * @param status       OAuth flow status (success, link_success, error)
     * @param error        Error message if status=error
     * @return Redirect to frontend with opaque tokens
     */
    @GetMapping("/success")
    public Mono<Void> oauthSuccess(
            @RequestParam(required = false) String accessToken,
            @RequestParam(required = false) String refreshToken,
            @RequestParam String status,
            @RequestParam(required = false) String error,
            ServerWebExchange exchange) {

        log.info("[Gateway OAuth] Received callback: status={}", status);
        ServerHttpResponse response = exchange.getResponse();

        // If error status, redirect directly
        if ("error".equals(status)) {
            String url = buildFrontendUrl(null, null, error, status);
            return redirectTo(response, url);
        }

        // If link_success (no tokens), redirect directly
        if ("link_success".equals(status)) {
            String url = buildFrontendUrl(null, null, null, status);
            return redirectTo(response, url);
        }

        // Convert JWT tokens to opaque
        Mono<String> opaqueAccessMono = accessToken != null
                ? opaqueTokenService.mint(accessToken, "access")
                : Mono.just("");

        Mono<String> opaqueRefreshMono = refreshToken != null
                ? opaqueTokenService.mint(refreshToken, "refresh")
                : Mono.just("");

        return Mono.zip(opaqueAccessMono, opaqueRefreshMono)
                .flatMap(tuple -> {
                    String opaqueAccess = tuple.getT1();
                    String opaqueRefresh = tuple.getT2();

                    log.info("[Gateway OAuth] Converted tokens to opaque: access={}, refresh={}",
                            opaqueAccess.substring(0, Math.min(8, opaqueAccess.length())),
                            opaqueRefresh.substring(0, Math.min(8, opaqueRefresh.length())));

                    String url = buildFrontendUrl(opaqueAccess, opaqueRefresh, null, status);
                    return redirectTo(response, url);
                })
                .doOnError(err -> log.error("[Gateway OAuth] Token conversion error: {}", err.getMessage()));
    }

    /**
     * Perform 302 redirect using ServerHttpResponse
     */
    private Mono<Void> redirectTo(ServerHttpResponse response, String url) {
        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().setLocation(URI.create(url));
        log.info("[Gateway OAuth] Redirecting to: {}", url.replaceAll("token=[^&]+", "token=***"));
        return response.setComplete();
    }

    /**
     * Build frontend callback URL with parameters
     */
    private String buildFrontendUrl(String accessToken, String refreshToken, String error, String status) {
        StringBuilder url = new StringBuilder(frontendBaseUrl);
        url.append("/oauth/callback?status=").append(status);

        if (accessToken != null && !accessToken.isEmpty()) {
            url.append("&token=").append(URLEncoder.encode(accessToken, StandardCharsets.UTF_8));
        }

        if (refreshToken != null && !refreshToken.isEmpty()) {
            url.append("&refreshToken=").append(URLEncoder.encode(refreshToken, StandardCharsets.UTF_8));
        }

        if (error != null && !error.isEmpty()) {
            url.append("&error=").append(URLEncoder.encode(error, StandardCharsets.UTF_8));
        }

        return url.toString();
    }
}
