package org.workfitai.authservice.service;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class RefreshTokenServiceIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:6.2.7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.redis.host", () -> redis.getHost());
        registry.add("spring.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void cleanup() {
        // flush all keys so tests stay isolated
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }

    @Test
    void storeAndGetUserId() {
        String token = "tok123";
        String userId = "user-xyz";

        refreshTokenService.store(token, userId);

        // Redis key should exist
        String stored = refreshTokenService.getUserId(token);
        assertThat(stored).isEqualTo(userId);
    }

    @Test
    void deleteRemovesKey() {
        String token = "tokDel";
        refreshTokenService.store(token, "any");

        refreshTokenService.delete(token);
        assertThat(refreshTokenService.getUserId(token)).isNull();
    }
}