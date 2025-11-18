package org.workfitai.apigateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.web.bind.annotation.*;
import org.workfitai.apigateway.service.IOpaqueTokenService;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Debug controller for testing token operations
 */
@RestController
@RequestMapping("/debug")
@RequiredArgsConstructor
@Slf4j
public class DebugController {

    private final IOpaqueTokenService opaqueTokenService;
    private final ReactiveStringRedisTemplate redis;

    @PostMapping("/mint-test")
    public Mono<Map<String, String>> testMint(@RequestBody Map<String, String> request) {
        String jwt = request.get("jwt");
        String kind = request.getOrDefault("kind", "access");

        log.info("[DEBUG] Testing mint with JWT: {}", jwt.substring(0, Math.min(20, jwt.length())));

        return opaqueTokenService.mint(jwt, kind)
                .map(opaque -> Map.of(
                        "opaque", opaque,
                        "jwt", jwt,
                        "kind", kind,
                        "redisKey", String.format("opaque:%s:%s", kind, opaque)));
    }

    @GetMapping("/lookup-test/{opaque}")
    public Mono<Map<String, Object>> testLookup(@PathVariable String opaque) {
        log.info("[DEBUG] Testing lookup for opaque: {}", opaque);

        return opaqueTokenService.toJwt(opaque)
                .map(jwt -> Map.<String, Object>of(
                        "opaque", opaque,
                        "found", true,
                        "jwt", jwt.substring(0, Math.min(20, jwt.length())) + "..."))
                .defaultIfEmpty(Map.of(
                        "opaque", opaque,
                        "found", false,
                        "message", "No JWT found in Redis"));
    }

    @GetMapping("/redis-keys")
    public Mono<Map<String, Object>> listRedisKeys() {
        return redis.keys("opaque:*")
                .collectList()
                .map(keys -> Map.<String, Object>of(
                        "totalKeys", keys.size(),
                        "keys", keys));
    }

    @GetMapping("/redis-get/{key}")
    public Mono<Map<String, String>> getRedisValue(@PathVariable String key) {
        return redis.opsForValue().get(key)
                .map(value -> Map.of(
                        "key", key,
                        "value", value.substring(0, Math.min(50, value.length())) + (value.length() > 50 ? "..." : "")))
                .defaultIfEmpty(Map.of(
                        "key", key,
                        "value", "NOT_FOUND"));
    }
}