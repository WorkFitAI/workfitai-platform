package org.workfitai.applicationservice.validation;

import org.springframework.stereotype.Component;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;

import lombok.extern.slf4j.Slf4j;

/**
 * Validates application status transitions based on business rules.
 *
 * Valid transition flow:
 * APPLIED → REVIEWING → INTERVIEW → OFFER → HIRED
 *    ↓         ↓           ↓         ↓
 * REJECTED  REJECTED   REJECTED  REJECTED
 *
 * Terminal states (HIRED, REJECTED) cannot transition to any other status.
 */
@Component
@Slf4j
public class StatusTransitionValidator {

    /**
     * Validates if a status transition is allowed based on business rules.
     *
     * Valid transitions:
     * - APPLIED → REVIEWING, REJECTED
     * - REVIEWING → INTERVIEW, REJECTED
     * - INTERVIEW → OFFER, REJECTED
     * - OFFER → HIRED, REJECTED
     * - HIRED → (terminal state, no transitions)
     * - REJECTED → (terminal state, no transitions)
     *
     * @param currentStatus Current application status
     * @param newStatus Target status
     * @throws IllegalArgumentException if transition is not allowed
     */
    public void validateTransition(ApplicationStatus currentStatus, ApplicationStatus newStatus) {
        log.debug("Validating status transition: {} → {}", currentStatus, newStatus);

        // Same status is not a valid transition
        if (currentStatus == newStatus) {
            throw new IllegalArgumentException(
                    String.format("Status is already %s", currentStatus));
        }

        // Terminal states cannot transition
        if (currentStatus == ApplicationStatus.HIRED || currentStatus == ApplicationStatus.REJECTED) {
            throw new IllegalArgumentException(
                    String.format("Cannot change status from terminal state %s", currentStatus));
        }

        // Define valid transitions
        boolean isValidTransition = switch (currentStatus) {
            case APPLIED -> newStatus == ApplicationStatus.REVIEWING || newStatus == ApplicationStatus.REJECTED;
            case REVIEWING -> newStatus == ApplicationStatus.INTERVIEW || newStatus == ApplicationStatus.REJECTED;
            case INTERVIEW -> newStatus == ApplicationStatus.OFFER || newStatus == ApplicationStatus.REJECTED;
            case OFFER -> newStatus == ApplicationStatus.HIRED || newStatus == ApplicationStatus.REJECTED;
            default -> false;
        };

        if (!isValidTransition) {
            throw new IllegalArgumentException(
                    String.format("Invalid status transition from %s to %s", currentStatus, newStatus));
        }

        log.debug("Status transition validation passed: {} → {}", currentStatus, newStatus);
    }

    /**
     * Checks if a transition is valid without throwing an exception.
     *
     * @param currentStatus Current application status
     * @param newStatus Target status
     * @return true if transition is allowed, false otherwise
     */
    public boolean isValidTransition(ApplicationStatus currentStatus, ApplicationStatus newStatus) {
        try {
            validateTransition(currentStatus, newStatus);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
