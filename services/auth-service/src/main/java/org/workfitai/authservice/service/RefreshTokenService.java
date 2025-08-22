package org.workfitai.authservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RefreshTokenService {

    private static final String KEY_FMT = "auth:rt:%s:%s"; // userId, deviceId

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public RefreshTokenService(StringRedisTemplate redis,
                               @Value("${auth.jwt.refresh-exp-ms}") long refreshExpMs) {
        this.redis = redis;
        this.ttl = Duration.ofMillis(refreshExpMs);
    }

    private String key(String userId, String deviceId) {
        return String.format(KEY_FMT, userId, deviceId);
    }

    /** Save/overwrite the active jti for this user+device */
    public void saveJti(String userId, String deviceId, String jti) {
        redis.opsForValue().set(key(userId, deviceId), jti, ttl);
    }

    /** Get the currently active jti for this user+device */
    public String getJti(String userId, String deviceId) {
        return redis.opsForValue().get(key(userId, deviceId));
    }

    /** Remove the device binding (used by logout later) */
    public void delete(String userId, String deviceId) {
        redis.delete(key(userId, deviceId));
    }
}
