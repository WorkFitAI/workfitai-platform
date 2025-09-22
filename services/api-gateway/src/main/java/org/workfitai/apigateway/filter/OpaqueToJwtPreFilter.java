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

  @Bean
  public GlobalFilter opaqueToJwtFilter() {
    return new OpaqueToJwtFilterImpl(opaqueTokenService);
  }

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
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
      String auth = exchange.getRequest().getHeaders().getFirst("Authorization");
      if (!StringUtils.hasText(auth) || !auth.startsWith("Bearer ")) {
        return chain.filter(exchange);
      }
      String token = auth.substring(7).trim();

      // Nếu là JWT (có 2 dấu chấm) → bỏ qua
      if (token.chars().filter(ch -> ch == '.').count() == 2) {
        return chain.filter(exchange);
      }

      // Lưu opaque để post filter revoke sau khi /auth/logout thành công
      exchange.getAttributes().put(ATTR_OPAQUE_USED, token);

      // Đổi opaque → jwt
      return service.toJwt(token)
          .defaultIfEmpty("")
          .flatMap(jwt -> {
            if (!StringUtils.hasText(jwt)) {
              log.debug("[OpaqueToJwtPre] opaque not found, pass-through");
              return chain.filter(exchange);
            }
            ServerHttpRequest mutated = exchange.getRequest().mutate()
                .headers(h -> {
                  h.set("Authorization", "Bearer " + jwt);
                  h.set("X-Token-Source", "opaque"); // optional
                })
                .build();
            return chain.filter(exchange.mutate().request(mutated).build());
          });
    }
  }
}
