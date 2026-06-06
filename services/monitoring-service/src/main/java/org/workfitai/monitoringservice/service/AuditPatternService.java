package org.workfitai.monitoringservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.workfitai.monitoringservice.dto.AuditPatternDto;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Manages configurable audit log display patterns.
 *
 * Default messages are loaded from audit-patterns.properties at startup.
 * Admins can override individual messages at runtime; overrides survive
 * restarts via Redis (key: audit:patterns).
 * Resolution order: Redis custom value → file default → empty.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditPatternService {

    static final String REDIS_HASH_KEY = "audit:patterns";
    private static final String PATTERNS_FILE = "audit-patterns.properties";

    /**
     * Default display messages loaded from audit-patterns.properties.
     * Insertion order matches the file — edit the file to add/reorder patterns.
     */
    static final Map<String, String> DEFAULT_PATTERNS;

    static {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        try (InputStream is = AuditPatternService.class.getClassLoader().getResourceAsStream(PATTERNS_FILE);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(Objects.requireNonNull(is, PATTERNS_FILE + " not found on classpath"),
                             StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq > 0) {
                    m.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
                }
            }
        } catch (Exception e) {
            throw new ExceptionInInitializerError("Cannot load " + PATTERNS_FILE + ": " + e.getMessage());
        }
        DEFAULT_PATTERNS = Collections.unmodifiableMap(m);
    }

    private final StringRedisTemplate redisTemplate;

    /**
     * Resolve the display message for a pattern key.
     * Returns the admin-customized message when available, otherwise the default.
     */
    public Optional<String> resolveMessage(String key) {
        try {
            Object custom = redisTemplate.opsForHash().get(REDIS_HASH_KEY, key);
            if (custom instanceof String s) {
                return Optional.of(s);
            }
        } catch (Exception e) {
            log.warn("Redis lookup failed for pattern '{}', using default", key);
        }
        return Optional.ofNullable(DEFAULT_PATTERNS.get(key));
    }

    /** Returns all known patterns merged with any admin customizations. */
    public List<AuditPatternDto> getAllPatterns() {
        Map<Object, Object> customs = Collections.emptyMap();
        try {
            customs = redisTemplate.opsForHash().entries(REDIS_HASH_KEY);
        } catch (Exception e) {
            log.warn("Redis unavailable, returning default patterns only");
        }

        final Map<Object, Object> customSnapshot = customs;
        return DEFAULT_PATTERNS.entrySet().stream()
                .map(entry -> {
                    String key = entry.getKey();
                    String defaultMsg = entry.getValue();
                    Object customObj = customSnapshot.get(key);
                    boolean customized = customObj instanceof String;
                    String current = customized ? (String) customObj : defaultMsg;
                    return new AuditPatternDto(key, current, defaultMsg, customized);
                })
                .collect(Collectors.toList());
    }

    /** Returns a single pattern, or empty when the key is unknown. */
    public Optional<AuditPatternDto> getPattern(String key) {
        if (!DEFAULT_PATTERNS.containsKey(key)) {
            return Optional.empty();
        }
        String defaultMsg = DEFAULT_PATTERNS.get(key);
        try {
            Object customObj = redisTemplate.opsForHash().get(REDIS_HASH_KEY, key);
            if (customObj instanceof String custom) {
                return Optional.of(new AuditPatternDto(key, custom, defaultMsg, true));
            }
        } catch (Exception e) {
            log.warn("Redis lookup failed for pattern '{}'", key);
        }
        return Optional.of(new AuditPatternDto(key, defaultMsg, defaultMsg, false));
    }

    /**
     * Override the display message for an existing pattern key.
     *
     * @throws IllegalArgumentException when the key is not in DEFAULT_PATTERNS
     */
    public AuditPatternDto updatePattern(String key, String message) {
        if (!DEFAULT_PATTERNS.containsKey(key)) {
            throw new IllegalArgumentException("Unknown pattern key: " + key);
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        redisTemplate.opsForHash().put(REDIS_HASH_KEY, key, message.trim());
        return new AuditPatternDto(key, message.trim(), DEFAULT_PATTERNS.get(key), true);
    }

    /** Remove the custom override for one pattern, reverting to its default. */
    public AuditPatternDto resetPattern(String key) {
        if (!DEFAULT_PATTERNS.containsKey(key)) {
            throw new IllegalArgumentException("Unknown pattern key: " + key);
        }
        redisTemplate.opsForHash().delete(REDIS_HASH_KEY, key);
        String defaultMsg = DEFAULT_PATTERNS.get(key);
        return new AuditPatternDto(key, defaultMsg, defaultMsg, false);
    }

    /** Remove all custom overrides, reverting every pattern to its default. */
    public void resetAll() {
        redisTemplate.delete(REDIS_HASH_KEY);
    }
}
