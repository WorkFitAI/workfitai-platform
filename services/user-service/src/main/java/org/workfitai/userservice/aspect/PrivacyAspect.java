package org.workfitai.userservice.aspect;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.workfitai.userservice.dto.response.CandidateResponse;
import org.workfitai.userservice.dto.response.HRResponse;
import org.workfitai.userservice.exception.ForbiddenException;
import org.workfitai.userservice.model.UserEntity;
import org.workfitai.userservice.repository.UserRepository;

/**
 * AOP Aspect to enforce privacy settings on user profile endpoints.
 * Intercepts @CheckPrivacy annotated methods and applies privacy rules.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class PrivacyAspect {

    private final UserRepository userRepository;

    @Around("@annotation(org.workfitai.userservice.annotation.CheckPrivacy)")
    public Object enforcePrivacy(ProceedingJoinPoint joinPoint) throws Throwable {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String currentUsername = auth != null ? auth.getName() : null;

        log.debug("Privacy aspect invoked by user: {}", currentUsername);

        // Proceed with method execution
        Object result = joinPoint.proceed();

        // Apply privacy filtering to response
        if (result == null) {
            return null;
        }

        // Handle ResponseEntity wrapper
        if (result instanceof org.springframework.http.ResponseEntity) {
            org.springframework.http.ResponseEntity<?> responseEntity = (org.springframework.http.ResponseEntity<?>) result;
            Object body = responseEntity.getBody();

            if (body == null) {
                return result;
            }

            // Handle ResponseData wrapper
            if (body instanceof org.workfitai.userservice.dto.response.ResponseData) {
                org.workfitai.userservice.dto.response.ResponseData<?> responseData = (org.workfitai.userservice.dto.response.ResponseData<?>) body;
                Object data = responseData.getData();

                if (data instanceof CandidateResponse) {
                    applyPrivacyToCandidate((CandidateResponse) data, currentUsername);
                } else if (data instanceof HRResponse) {
                    applyPrivacyToHR((HRResponse) data, currentUsername);
                } else if (data instanceof org.springframework.data.domain.Page) {
                    applyPrivacyToPage((org.springframework.data.domain.Page<?>) data, currentUsername);
                }
            }
        }

        return result;
    }

    private void applyPrivacyToCandidate(CandidateResponse candidate, String currentUsername) {
        if (candidate == null || candidate.getUsername() == null) {
            return;
        }

        // Owner always sees everything
        if (candidate.getUsername().equals(currentUsername)) {
            log.debug("User viewing own profile - no privacy filter");
            return;
        }

        // Get privacy settings from database
        UserEntity user = userRepository.findByUsername(candidate.getUsername()).orElse(null);
        if (user == null || user.getPrivacySettings() == null) {
            log.debug("No privacy settings found for user: {}, applying PUBLIC default", candidate.getUsername());
            return; // Default is PUBLIC - show all
        }

        JsonNode privacySettings = user.getPrivacySettings();
        String profileVisibility = privacySettings.has("profileVisibility")
                ? privacySettings.get("profileVisibility").asText()
                : "PUBLIC";

        log.debug("Applying privacy filter for user: {}, visibility: {}", candidate.getUsername(), profileVisibility);

        if ("PRIVATE".equals(profileVisibility)) {
            // Hide all sensitive data
            candidate.setPhoneNumber(null);
            candidate.setEmail(null);
            candidate.setAddress(null);
            candidate.setDateOfBirth(null);
            candidate.setGender(null);
            candidate.setTotalExperience(null);
            log.debug("Applied PRIVATE privacy filter for user: {}", candidate.getUsername());

        } else if ("CONNECTIONS_ONLY".equals(profileVisibility)) {
            // TODO: Check if current user is connected to this candidate
            // For now, hide sensitive data
            candidate.setPhoneNumber(null);
            candidate.setEmail(null);
            candidate.setAddress(null);
            log.debug("Applied CONNECTIONS_ONLY privacy filter for user: {}", candidate.getUsername());
        }
        // PUBLIC - no filtering
    }

    private void applyPrivacyToHR(HRResponse hr, String currentUsername) {
        if (hr == null || hr.getUsername() == null) {
            return;
        }

        // Owner always sees everything
        if (hr.getUsername().equals(currentUsername)) {
            return;
        }

        // Get privacy settings
        UserEntity user = userRepository.findByUsername(hr.getUsername()).orElse(null);
        if (user == null || user.getPrivacySettings() == null) {
            return; // Default PUBLIC
        }

        JsonNode privacySettings = user.getPrivacySettings();
        String profileVisibility = privacySettings.has("profileVisibility")
                ? privacySettings.get("profileVisibility").asText()
                : "PUBLIC";

        if ("PRIVATE".equals(profileVisibility)) {
            hr.setPhoneNumber(null);
            hr.setEmail(null);
            hr.setAddress(null);
            log.debug("Applied PRIVATE privacy filter for HR user: {}", hr.getUsername());

        } else if ("CONNECTIONS_ONLY".equals(profileVisibility)) {
            hr.setPhoneNumber(null);
            hr.setEmail(null);
            log.debug("Applied CONNECTIONS_ONLY privacy filter for HR user: {}", hr.getUsername());
        }
    }

    private void applyPrivacyToPage(org.springframework.data.domain.Page<?> page, String currentUsername) {
        if (page == null || page.isEmpty()) {
            return;
        }

        page.getContent().forEach(item -> {
            if (item instanceof CandidateResponse) {
                applyPrivacyToCandidate((CandidateResponse) item, currentUsername);
            } else if (item instanceof HRResponse) {
                applyPrivacyToHR((HRResponse) item, currentUsername);
            }
        });
    }
}
