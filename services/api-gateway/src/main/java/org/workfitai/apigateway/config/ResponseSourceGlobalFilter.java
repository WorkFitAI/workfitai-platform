package org.workfitai.apigateway.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.workfitai.apigateway.service.IOpaqueTokenService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class ResponseSourceGlobalFilter {

  private final ObjectMapper mapper;
  private final IOpaqueTokenService opaqueTokenService;

  @Bean
  public GlobalFilter responseSourceFilter() {
    // order = -2 (trước NettyWriteResponseFilter -1)
    return new DecoratingResponseFilter(mapper, opaqueTokenService);
  }

  static final class DecoratingResponseFilter implements GlobalFilter, Ordered {

    private final ObjectMapper mapper;
    private final IOpaqueTokenService opaqueTokenService;

    DecoratingResponseFilter(ObjectMapper mapper, IOpaqueTokenService svc) {
      this.mapper = mapper;
      this.opaqueTokenService = svc;
    }

    @Override
    public int getOrder() {
      return org.springframework.cloud.gateway.filter.NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1; // -2
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
      // Lấy Route và routeId
      Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
      String routeId = (route != null) ? route.getId() : null;

      if (!StringUtils.hasText(routeId)) {
        String path = exchange.getRequest().getPath().value();
        String[] segments = path.split("/");
        routeId = (segments.length > 1) ? segments[1] : "unknown";
      }

      // Chuẩn hoá "source": bỏ prefix mặc định của Discovery
      String source = routeId.replaceFirst("^ReactiveCompositeDiscoveryClient_", "");
      final String finalSource = source;
      final String finalRouteId = routeId;

      ServerHttpResponse original = exchange.getResponse();
      DataBufferFactory bufferFactory = original.bufferFactory();

      ServerHttpResponseDecorator decorated = new ServerHttpResponseDecorator(original) {

        @Override
        public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
          // ✅ luôn convert sang Flux để hỗ trợ cả Mono<DataBuffer>
          Flux<? extends DataBuffer> fluxBody = Flux.from(body);

          return super.writeWith(
              fluxBody.collectList()
                  .flatMap(list -> {
                    byte[] content = merge(list);

                    MediaType ct = getHeaders().getContentType();
                    log.debug("[ResponseSourceFilter] routeId={}, source={}, Content-Type={}", finalRouteId, finalSource, ct);

                    // Nếu header thể hiện rõ là binary/file → pass-through
                    if (ct != null) {
                      String cts = ct.toString().toLowerCase();
                      if (cts.contains("pdf") || cts.contains("octet-stream") ||
                          cts.startsWith("image/") || cts.startsWith("video/")) {
                        return Mono.just(bufferFactory.wrap(content));
                      }
                    }

                    // Cứ thử parse JSON (kể cả khi CT null). Parse fail → trả nguyên
                    JsonNode parsed;
                    try {
                      parsed = mapper.readTree(content);
                    } catch (Exception e) {
                      log.debug("[ResponseSourceFilter] routeId={} parse fail: {}", finalRouteId, e.getMessage());
                      return Mono.just(bufferFactory.wrap(content));
                    }

                    int httpStatus = (getStatusCode() != null) ? getStatusCode().value() : 200;

                    Mono<JsonNode> processed;
                    if (parsed.has("status") && parsed.has("message")) {
                      // ĐÃ là ResponseData → chỉ thêm "source" + opaque hóa (deep)
                      ((ObjectNode) parsed).put("source", finalSource);
                      processed = opaqueTokensIfPresentDeep(parsed);
                    } else {
                      // KHÔNG phải ResponseData → wrap lại + opaque hóa (deep)
                      ObjectNode wrapper = mapper.createObjectNode();
                      wrapper.put("status", httpStatus);
                      wrapper.put("message", httpStatus < 400 ? "Success" : "Error");
                      wrapper.set("data", parsed);
                      wrapper.put("source", finalSource);
                      wrapper.put("timestamp", LocalDateTime.now().toString());
                      processed = opaqueTokensIfPresentDeep(wrapper);
                    }

                    return processed.map(n -> {
                      try {
                        byte[] bytes = mapper.writeValueAsBytes(n);
                        getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        getHeaders().remove("Content-Length");
                        return bufferFactory.wrap(bytes);
                      } catch (Exception e) {
                        log.debug("[ResponseSourceFilter] writeValueAsBytes fail: {}", e.getMessage());
                        return bufferFactory.wrap(content);
                      }
                    });
                  })
                  .flux()
          );
        }

        @Override
        public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
          // một số handler ghi theo luồng này → hợp nhất rồi tái dùng writeWith
          return writeWith(Flux.from(body).flatMapSequential(p -> p));
        }

        /** Opaque-hoá token theo kiểu đệ quy: bắt hầu hết key phổ biến & biến thể */
        private Mono<JsonNode> opaqueTokensIfPresentDeep(JsonNode root) {
          ObjectNode target = root.isObject() ? (ObjectNode) root : mapper.createObjectNode().put("status", 200).put("message", "Success");
          if (!root.isObject()) {
            target.set("data", root);
            target.put("timestamp", LocalDateTime.now().toString());
          }

          // container: ưu tiên data nếu có
          ObjectNode container = target;
          if (target.has("data") && target.get("data").isObject()) {
            container = (ObjectNode) target.get("data");
          }

          List<Supplier<Mono<Boolean>>> jobs = new ArrayList<>();
          traverseAndCollectMintJobs(container, jobs);

          if (jobs.isEmpty()) {
            return Mono.just(target);
          }

          Mono<Boolean> chain = Mono.just(false);
          for (Supplier<Mono<Boolean>> job : jobs) {
            chain = chain.flatMap(changedSoFar -> job.get().map(changedThis -> changedSoFar || changedThis));
          }

          ObjectNode finalTarget = target;
          ObjectNode finalContainer = container;

          return chain.map(anyChanged -> {
            if (anyChanged) {
              if (finalContainer.has("tokenType")) finalContainer.put("tokenType", "Opaque");
              else finalTarget.put("tokenType", "Opaque");
            }
            return (JsonNode) finalTarget;
          });
        }

        /** duyệt đệ quy, gom các mint-job cho những field có thể là token */
        private void traverseAndCollectMintJobs(JsonNode node, List<Supplier<Mono<Boolean>>> jobs) {
          if (node == null) return;

          if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
              var e = fields.next();
              String key = e.getKey();
              JsonNode child = e.getValue();

              if (looksLikeTokenKey(key) && child.isTextual() && looksLikeJwt(child.asText())) {
                String kind = key.toLowerCase().contains("refresh") ? "refresh" : "access";
                jobs.add(() -> mintAndReplace((ObjectNode) node, key, child.asText(), kind));
              } else {
                traverseAndCollectMintJobs(child, jobs);
              }
            }
          } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
              traverseAndCollectMintJobs(node.get(i), jobs);
            }
          }
        }

        /** thực hiện mint và replace, trả true nếu có thay đổi */
        private Mono<Boolean> mintAndReplace(ObjectNode obj, String key, String jwt, String kind) {
          return opaqueTokenService.mint(jwt, kind)
              .map(opaque -> {
                if (opaque != null && !opaque.isEmpty()) {
                  obj.put(key, opaque);
                  log.debug("[opaque] replaced key='{}' kind={} → opaque", key, kind);
                  return true;
                }
                return false;
              })
              .defaultIfEmpty(false);
        }

        private boolean looksLikeTokenKey(String key) {
          String k = key.toLowerCase();
          return k.equals("accesstoken") || k.equals("refreshtoken") || k.equals("idtoken")
              || k.equals("access_token") || k.equals("refresh_token") || k.equals("id_token")
              || k.equals("token") || k.equals("jwt") || k.equals("bearertoken") || k.equals("bearer_token")
              || (k.contains("access") && k.contains("token"))
              || (k.contains("refresh") && k.contains("token"))
              || (k.contains("id") && k.contains("token"))
              || k.equals("authorization");
        }

        private boolean looksLikeJwt(String s) {
          if (s == null) return false;
          int dots = (int) s.chars().filter(ch -> ch == '.').count();
          return dots == 2 && s.length() > 20; // hình dạng "header.payload.sig"
        }

        private byte[] merge(List<? extends DataBuffer> list) {
          int total = list.stream().mapToInt(DataBuffer::readableByteCount).sum();
          byte[] content = new byte[total];
          int offset = 0;
          for (DataBuffer b : list) {
            int count = b.readableByteCount();
            b.read(content, offset, count);
            offset += count;
            DataBufferUtils.release(b);
          }
          return content;
        }
      };

      return chain.filter(exchange.mutate().response(decorated).build());
    }

    // giữ lại util cũ nếu bạn còn dùng chỗ khác
    @SuppressWarnings("unused")
    private static boolean looksLikeJson(byte[] body) {
      if (body == null || body.length == 0) return false;
      String s = new String(body, StandardCharsets.UTF_8).trim();
      return (s.startsWith("{") && s.endsWith("}")) || (s.startsWith("[") && s.endsWith("]"));
    }
  }
}
