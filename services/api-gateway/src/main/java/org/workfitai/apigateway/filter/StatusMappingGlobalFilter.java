package org.workfitai.apigateway.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.reactivestreams.Publisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
public class StatusMappingGlobalFilter implements GlobalFilter, Ordered {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @ConditionalOnProperty(
            value = "gateway.status-mapping.enabled",
            havingValue = "true",
            matchIfMissing = false
    )
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponse originalResponse = exchange.getResponse();
        DataBufferFactory bufferFactory = originalResponse.bufferFactory();

        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(@NonNull Publisher<? extends DataBuffer> body) {
                if (!(body instanceof Flux<?>)) {
                    return super.writeWith(body);
                }

                // Only attempt to transform when Content-Type is JSON.
                MediaType contentType = originalResponse.getHeaders().getContentType();
                boolean intendsJson = contentType != null &&
                        (MediaType.APPLICATION_JSON.includes(contentType) ||
                                (contentType.getSubtype() != null &&
                                        contentType.getSubtype().toLowerCase().contains("json")));

                if (!intendsJson) {
                    // Pass through untouched for non-JSON bodies (HTML, files, streams, etc.)
                    return super.writeWith(body);
                }

                return DataBufferUtils.join((Flux<? extends DataBuffer>) body)
                        .flatMap(dataBuffer -> {
                            byte[] bytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(bytes);
                            DataBufferUtils.release(dataBuffer);
                            String responseBody = new String(bytes, StandardCharsets.UTF_8);

                            // Empty body → nothing to do
                            if (responseBody.isBlank()) {
                                return originalResponse.writeWith(Mono.just(bufferFactory.wrap(bytes)));
                            }

                            try {
                                // Try to parse JSON; if it fails, just pass through (fail-open)
                                JsonNode json = objectMapper.readTree(responseBody);

                                // If the body contains a "status" field, map it to HTTP status (only if valid)
                                if (json.has("status") && json.get("status").canConvertToInt()) {
                                    int customStatus = json.get("status").asInt();
                                    HttpStatus resolved = HttpStatus.resolve(customStatus);
                                    if (resolved != null) {
                                        originalResponse.setStatusCode(resolved);
                                    }
                                }

                                byte[] newBody = objectMapper.writeValueAsBytes(json);
                                DataBuffer buffer = bufferFactory.wrap(newBody);
                                return originalResponse.writeWith(Mono.just(buffer));
                            } catch (Exception parseError) {
                                // Parsing failed (likely not JSON despite header) → write original body
                                DataBuffer buffer = bufferFactory.wrap(bytes);
                                return originalResponse.writeWith(Mono.just(buffer));
                            }
                        });
            }
        };

        ServerWebExchange mutatedExchange = exchange.mutate().response(decoratedResponse).build();
        return chain.filter(mutatedExchange);
    }

    @Override
    public int getOrder() {
        // Keep high precedence so it runs before default response handling
        return -2;
    }
}