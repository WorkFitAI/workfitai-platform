package org.workfitai.apigateway.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
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

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponse originalResponse = exchange.getResponse();
        DataBufferFactory bufferFactory = originalResponse.bufferFactory();

        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(@NonNull Publisher<? extends DataBuffer> body) {
                if (body instanceof Flux<?>) {
                    return DataBufferUtils.join((Flux<? extends DataBuffer>) body)
                            .flatMap(dataBuffer -> {
                                byte[] bytes = new byte[dataBuffer.readableByteCount()];
                                dataBuffer.read(bytes);
                                DataBufferUtils.release(dataBuffer);
                                String responseBody = new String(bytes, StandardCharsets.UTF_8);

                                try {
                                    JsonNode json = objectMapper.readTree(responseBody);
                                    int customStatus = json.has("status") ? json.get("status").asInt(200) : 200;

                                    if (customStatus != 200) {
                                        originalResponse.setStatusCode(HttpStatus.resolve(customStatus));
                                    }

                                    byte[] newBody = objectMapper.writeValueAsBytes(json);
                                    DataBuffer buffer = bufferFactory.wrap(newBody);
                                    return originalResponse.writeWith(Mono.just(buffer));
                                } catch (Exception e) {
                                    return Mono.error(e);
                                }
                            });
                }

                return super.writeWith(body);
            }
        };

        ServerWebExchange mutatedExchange = exchange.mutate().response(decoratedResponse).build();
        return chain.filter(mutatedExchange);
    }

    @Override
    public int getOrder() {
        return -2; // Ensure this filter runs before the default response handling
    }
}