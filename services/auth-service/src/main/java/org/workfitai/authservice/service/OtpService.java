package org.workfitai.authservice.service;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.workfitai.authservice.dto.request.PendingRegistration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class OtpService {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    private static final String OTP_PREFIX = "auth:otp:";
    private static final String PENDING_PREFIX = "auth:pending:";

    public OtpService(StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Value("${auth.otp.ttl-seconds:86400}") long ttlSeconds) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public String generateOtp() {
        return String.format("%06d", ThreadLocalRandom.current().nextInt(100000, 999999));
    }

    public void saveOtp(String email, String otp) {
        String key = OTP_PREFIX + email.toLowerCase();
        redis.opsForValue().set(key, otp, ttl);
    }

    public boolean verifyOtp(String email, String otp) {
        String key = OTP_PREFIX + email.toLowerCase();
        String storedOtp = redis.opsForValue().get(key);

        if (storedOtp != null && storedOtp.equals(otp)) {
            redis.delete(key); // Delete OTP after successful verification
            return true;
        }
        return false;
    }

    public void savePendingRegistration(String email, PendingRegistration pendingData) {
        try {
            String key = PENDING_PREFIX + email.toLowerCase();
            String serialized = objectMapper.writeValueAsString(pendingData);
            redis.opsForValue().set(key, serialized, ttl);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize pending registration data for {}", email, e);
            throw new IllegalStateException("Failed to store pending registration data", e);
        }
    }

    public PendingRegistration getPendingRegistration(String email) {
        try {
            String key = PENDING_PREFIX + email.toLowerCase();
            String data = redis.opsForValue().get(key);
            if (data == null)
                return null;

            return objectMapper.readValue(data, PendingRegistration.class);
        } catch (Exception e) {
            log.error("Failed to deserialize pending registration data for {}", email, e);
            return null;
        }
    }

    public void deletePendingRegistration(String email) {
        String key = PENDING_PREFIX + email.toLowerCase();
        redis.delete(key);
    }

    private String key(String email) {
        return "auth:otp:" + email.toLowerCase();
    }

    public <T> void saveOtp(String email, String otp, T payload) {
        try {
            OtpPayload<T> otpPayload = new OtpPayload<>(otp, payload);
            String serialized = objectMapper.writeValueAsString(otpPayload);
            redis.opsForValue().set(key(email), serialized, ttl);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize OTP payload for {}", email, e);
            throw new IllegalStateException("Failed to store OTP payload", e);
        }
    }

    public <T> OtpPayload<T> getOtp(String email, Class<T> payloadClass) {
        String data = redis.opsForValue().get(key(email));
        if (data == null)
            return null;
        try {
            return objectMapper.readerFor(objectMapper.getTypeFactory()
                    .constructParametricType(OtpPayload.class, payloadClass))
                    .readValue(data);
        } catch (Exception e) {
            log.error("Failed to deserialize OTP payload for {}", email, e);
            return null;
        }
    }

    public void deleteOtp(String email) {
        redis.delete(key(email));
    }

    public record OtpPayload<T>(String otp, T payload) {
    }
}
