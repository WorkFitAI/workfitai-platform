// src/main/java/org/workfitai/apigateway/filter/OpaqueToJwtPreFilter.java
package org.workfitai.apigateway.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.workfitai.apigateway.service.IOpaqueTokenService;
import reactor.core.publisher.Mono;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class OpaqueToJwtPreFilter {

  // key dùng chia sẻ giữa pre & post filter
  public static final String ATTR_OPAQUE_USED = "OPAQUE_ACCESS_USED";

  private final IOpaqueTokenService opaqueTokenService;

  // @Bean - DISABLED: Using OpaqueToJwtWebFilter instead which runs before
  // Security
  // public GlobalFilter opaqueToJwtFilter() {
  // return new OpaqueToJwtFilterImpl(opaqueTokenService);
  // }

  static final class OpaqueToJwtFilterImpl implements GlobalFilter, Ordered {
    private final IOpaqueTokenService service;

    OpaqueToJwtFilterImpl(IOpaqueTokenService service) {
      this.service = service;
    }

    @Override
    public int getOrder() {
      return Ordered.HIGHEST_PRECEDENCE;
    } // chạy trước security

    @Override
    @SuppressWarnings("null")
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
      String auth = exchange.getRequest().getHeaders().getFirst("Authorization");
      if (!StringUtils.hasText(auth)) {
        log.debug("[OpaqueToJwtPre] ❌ No Authorization header");
        return chain.filter(exchange);
      }

      // Extract token from "Bearer token" format
      final String token;
      if (auth.toLowerCase().startsWith("bearer ")) {
        token = auth.substring(7);
      } else {
        token = auth;
      }

      if (!StringUtils.hasText(token)) {
        log.debug("[OpaqueToJwtPre] ❌ Empty token after processing");
        return chain.filter(exchange);
      }

      // Check if it's already a JWT (has 2 dots and reasonable length)
      if (token.chars().filter(ch -> ch == '.').count() == 2 && token.length() > 50) {
        log.debug("[OpaqueToJwtPre] 🟢 Already JWT token, skip convert");
        return chain.filter(exchange);
      }

      // Store the opaque token for post-filter usage
      exchange.getAttributes().put(ATTR_OPAQUE_USED, token);
      log.info("[OpaqueToJwtPre] 🔄 Converting opaque -> jwt for token={}",
          token.substring(0, Math.min(8, token.length())));

      return service.toJwt(token)
          .doOnNext(jwt -> log.info("[OpaqueToJwtPre] ✅ Found JWT for opaque={}, jwt_prefix={}",
              token.substring(0, Math.min(8, token.length())), jwt.substring(0, Math.min(20, jwt.length()))))
          .defaultIfEmpty("")
          .flatMap(jwt -> {
            if (!StringUtils.hasText(jwt)) {
              log.warn("[OpaqueToJwtPre] ⚠️ No JWT found in Redis for opaque={}",
                  token.substring(0, Math.min(8, token.length())));
              return chain.filter(exchange);
            }

            ServerHttpRequest mutated = exchange.getRequest().mutate()
                .headers(h -> {
                  h.set("Authorization", "Bearer " + jwt);
                  h.set("X-Token-Source", "opaque");
                  h.set("X-Original-Token", token); // Keep original for debugging
                })
                .build();

            log.info("[OpaqueToJwtPre] 🧩 Injected JWT with Bearer prefix for downstream");
            return chain.filter(exchange.mutate().request(mutated).build());
          })
          .doOnError(err -> log.error("[OpaqueToJwtPre] 💥 Error converting opaque={}: {}",
              token.substring(0, Math.min(8, token.length())), err.getMessage()))
          .onErrorResume(err -> {
            log.error("[OpaqueToJwtPre] 🚨 Fallback: proceeding without conversion due to error");
            return chain.filter(exchange);
          });
    }

  }
}
