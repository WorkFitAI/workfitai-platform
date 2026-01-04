package org.workfitai.applicationservice.validation;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.workfitai.applicationservice.constants.Messages;
import org.workfitai.applicationservice.dto.request.CreateApplicationRequest;
import org.workfitai.applicationservice.exception.ApplicationConflictException;
import org.workfitai.applicationservice.repository.ApplicationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Validates that the user hasn't already applied to this job.
 * Order: 1 (runs first - quick local check)
 * 
 * NOTE: Only checks ACTIVE applications (deletedAt is null).
 * This allows candidates to reapply after withdrawing a previous application.
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class DuplicateApplicationValidator implements ApplicationValidator {

    private final ApplicationRepository applicationRepository;

    @Override
    public void validate(CreateApplicationRequest request, String username) {
        log.debug("Checking for duplicate application: user={}, job={}", username, request.getJobId());

        // Check only active applications (exclude withdrawn/soft-deleted ones)
        if (applicationRepository.existsByUsernameAndJobIdAndDeletedAtIsNull(username, request.getJobId())) {
            log.warn(Messages.Log.DUPLICATE_APPLICATION, username, request.getJobId());
            throw new ApplicationConflictException(Messages.Error.APPLICATION_ALREADY_EXISTS);
        }

        log.debug("No duplicate application found (withdrawn applications are ignored)");
    }

    @Override
    public int getOrder() {
        return 1;
    }
}
