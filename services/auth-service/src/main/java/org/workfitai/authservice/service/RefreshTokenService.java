package org.workfitai.authservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RefreshTokenService {

    private static final String REDIS_KEY_PREFIX = "refresh:";
    private final StringRedisTemplate redisTemplate;
    private final Duration refreshTokenTtl;

    public RefreshTokenService(StringRedisTemplate redisTemplate,
                               @Value("${auth.jwt.refresh-exp-ms}") long refreshExpMs) {
        this.redisTemplate = redisTemplate;
        this.refreshTokenTtl = Duration.ofMillis(refreshExpMs);
    }

    public void store(String token, String userId) {
        redisTemplate.opsForValue()
                .set(REDIS_KEY_PREFIX + token, userId, refreshTokenTtl);
    }

    public String getUserId(String token) {
        return redisTemplate.opsForValue().get(REDIS_KEY_PREFIX + token);
    }

    public void delete(String token) {
        redisTemplate.delete(REDIS_KEY_PREFIX + token);
    }
}