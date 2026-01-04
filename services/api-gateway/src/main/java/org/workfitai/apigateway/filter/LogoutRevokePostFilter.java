// src/main/java/org/workfitai/apigateway/filter/LogoutRevokePostFilter.java
package org.workfitai.apigateway.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.workfitai.apigateway.service.IOpaqueTokenService;
import reactor.core.publisher.Mono;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class LogoutRevokePostFilter {

  private static final AntPathMatcher PM = new AntPathMatcher();

  // chỉnh pattern tuỳ route của bạn; theo log trước đó, gateway map /auth/** → auth service
  private static final String[] LOGOUT_PATTERNS = {
      "/auth/logout",
      "**/auth/logout",   // nới lỏng nếu có tiền tố serviceId
      "*/logout"          // fallback nới lỏng
  };

  private final IOpaqueTokenService opaqueTokenService;

  @Bean
  public GlobalFilter logoutRevokeFilter() {
    return new LogoutRevokeFilterImpl(opaqueTokenService);
  }

  static final class LogoutRevokeFilterImpl implements GlobalFilter, Ordered {
    private final IOpaqueTokenService service;

    LogoutRevokeFilterImpl(IOpaqueTokenService service) {
      this.service = service;
    }

    @Override
    public int getOrder() {
      // chạy muộn để thực sự là "post" (sau khi downstream xử lý xong)
      return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
      String path = exchange.getRequest().getPath().value();

      boolean isLogout = false;
      for (String p : LOGOUT_PATTERNS) {
        if (PM.match(p, path)) {
          isLogout = true;
          break;
        }
      }
      if (!isLogout) {
        return chain.filter(exchange);
      }

      // cho downstream xử lý trước, rồi mới revoke nếu thành công
      return chain.filter(exchange).then(Mono.defer(() -> {
        HttpStatus st = (HttpStatus) exchange.getResponse().getStatusCode();
        int code = (st != null) ? st.value() : 200;

        if (code < 200 || code >= 300) {
          log.debug("[LogoutRevoke] skip revoke, downstream status={}", code);
          return Mono.empty();
        }

        // Lấy opaque access đã dùng (do pre-filter lưu lại)
        String accessOpaque = (String) exchange.getAttribute(OpaqueToJwtPreFilter.ATTR_OPAQUE_USED);

        // Optional: client có thể gửi thêm refresh opaque qua header này để xoá luôn
        String refreshOpaque = exchange.getRequest().getHeaders().getFirst("X-Refresh-Opaque");

        Mono<Void> m1 = Mono.empty();
        Mono<Void> m2 = Mono.empty();

        if (StringUtils.hasText(accessOpaque)) {
          // nếu service bạn có revokeAll(opaque) thì gọi một hàm là đủ
          m1 = service.revokeAll(accessOpaque)
              .doOnNext(cnt -> log.info("[LogoutRevoke] revoked accessOpaque={}, deletedKeys={}", accessOpaque, cnt))
              .then();
        }
        if (StringUtils.hasText(refreshOpaque)) {
          m2 = service.revokeAll(refreshOpaque)
              .doOnNext(cnt -> log.info("[LogoutRevoke] revoked refreshOpaque={}, deletedKeys={}", refreshOpaque, cnt))
              .then();
        }

        return Mono.when(m1, m2);
      }));
    }
  }
}
