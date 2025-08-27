// src/main/java/org/workfitai/apigateway/token/RedisOpaqueTokenService.java
package org.workfitai.apigateway.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import org.workfitai.apigateway.service.IOpaqueTokenService;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RedisOpaqueTokenService implements IOpaqueTokenService {

  private final ReactiveStringRedisTemplate redis;
  private final ObjectMapper mapper = new ObjectMapper();

  @Override
  public Mono<String> mint(String jwt, String kind) {
    String opaque = UUID.randomUUID().toString().replace("-", "");
    Duration ttl = ttlFromJwt(jwt).orElse(Duration.ofMinutes(15));
    String key = key(kind, opaque);
    return redis.opsForValue().set(key, jwt, ttl).thenReturn(opaque);
  }

  @Override
  public Mono<String> toJwt(String opaque) {
    // thử cả 2 loại
    return redis.opsForValue().get(key("access", opaque))
        .switchIfEmpty(redis.opsForValue().get(key("refresh", opaque)));
  }

  @Override
  public Mono<Long> revokeAll(String opaque) {
    return redis.delete(key("access", opaque), key("refresh", opaque));
  }

  private static String key(String kind, String opaque) {
    return "opaque:%s:%s".formatted(kind, opaque);
  }

  private Optional<Duration> ttlFromJwt(String jwt) {
    try {
      String[] parts = jwt.split("\\.");
      if (parts.length < 2) return Optional.empty();
      String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
      ObjectNode node = (ObjectNode) mapper.readTree(payloadJson);
      if (!node.has("exp")) return Optional.empty();
      long exp = node.get("exp").asLong();  // epoch seconds
      long now = Instant.now().getEpochSecond();
      long remain = Math.max(5, exp - now);
      return Optional.of(Duration.ofSeconds(remain));
    } catch (Exception e) {
      return Optional.empty();
    }
  }
}
