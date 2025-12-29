package org.workfitai.apigateway.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.factory.rewrite.MessageBodyDecoder;
import org.springframework.cloud.gateway.filter.factory.rewrite.MessageBodyEncoder;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyResponseBodyGatewayFilterFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.web.server.ServerWebExchange;
import org.workfitai.apigateway.service.IOpaqueTokenService;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class MintOpaquePostFilter {

  private final IOpaqueTokenService opaqueTokenService;
  private final ObjectProvider<List<HttpMessageReader<?>>> messageReaders;
  private final ObjectProvider<Set<MessageBodyDecoder>> bodyDecoders;
  private final ObjectProvider<Set<MessageBodyEncoder>> bodyEncoders;

  @Bean
  public GlobalFilter mintOpaqueFilter() {
    return new MintOpaqueFilterImpl(
        opaqueTokenService,
        messageReaders.getIfAvailable(),
        bodyDecoders.getIfAvailable(),
        bodyEncoders.getIfAvailable()
    );
  }

  static final class MintOpaqueFilterImpl implements GlobalFilter, Ordered {

    private final IOpaqueTokenService service;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ModifyResponseBodyGatewayFilterFactory factory;

    MintOpaqueFilterImpl(IOpaqueTokenService service,
                         List<HttpMessageReader<?>> readers,
                         Set<MessageBodyDecoder> decoders,
                         Set<MessageBodyEncoder> encoders) {
      this.service = service;
      this.factory = new ModifyResponseBodyGatewayFilterFactory(readers, decoders, encoders);
    }

    @Override
    public int getOrder() {
      return Ordered.LOWEST_PRECEDENCE - 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
      String path = exchange.getRequest().getPath().value();
      // Match login and OAuth exchange endpoints
      if (!path.matches(".*(/auth/login|/login|/auth/oauth/exchange)$")) {
        return chain.filter(exchange);
      }

      var config = new ModifyResponseBodyGatewayFilterFactory.Config();
      config.setRewriteFunction(String.class, String.class, (webExchange, originalBody) -> {
        if (originalBody == null || originalBody.isBlank()) return Mono.just("");

        try {
          JsonNode root = mapper.readTree(originalBody);
          if (root.has("data") && root.get("data").has("accessToken")) {
            String jwt = root.get("data").get("accessToken").asText();
            log.info("[MintOpaque] Found JWT, minting opaque...");

            return service.mint(jwt, "access").map(opaque -> {
              ((ObjectNode) root.get("data")).put("accessToken", opaque);
              try {
                String newBody = mapper.writeValueAsString(root);
                log.info("[MintOpaque] Mapped opaque={} for JWT", opaque);
                return newBody;
              } catch (Exception e) {
                log.error("[MintOpaque] JSON write error: {}", e.getMessage());
                return originalBody;
              }
            });
          }
        } catch (Exception e) {
          log.error("[MintOpaque] Parse error: {}", e.getMessage());
        }
        return Mono.just(originalBody);
      });

      return factory.apply(config).filter(exchange, chain)
          .doOnSuccess(v -> exchange.getResponse().setStatusCode(HttpStatus.OK));
    }
  }
}
