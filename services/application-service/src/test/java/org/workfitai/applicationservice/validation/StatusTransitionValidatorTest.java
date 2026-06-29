package org.workfitai.applicationservice.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;

class StatusTransitionValidatorTest {

    private final StatusTransitionValidator validator = new StatusTransitionValidator();

    // ─── Valid Transitions ────────────────────────────────────────────────────

    @Test
    void validate_appliedToReviewing_passes() {
        validator.validateTransition(ApplicationStatus.APPLIED, ApplicationStatus.REVIEWING);
    }

    @Test
    void validate_appliedToRejected_passes() {
        validator.validateTransition(ApplicationStatus.APPLIED, ApplicationStatus.REJECTED);
    }

    @Test
    void validate_reviewingToInterview_passes() {
        validator.validateTransition(ApplicationStatus.REVIEWING, ApplicationStatus.INTERVIEW);
    }

    @Test
    void validate_reviewingToRejected_passes() {
        validator.validateTransition(ApplicationStatus.REVIEWING, ApplicationStatus.REJECTED);
    }

    @Test
    void validate_interviewToOffer_passes() {
        validator.validateTransition(ApplicationStatus.INTERVIEW, ApplicationStatus.OFFER);
    }

    @Test
    void validate_interviewToRejected_passes() {
        validator.validateTransition(ApplicationStatus.INTERVIEW, ApplicationStatus.REJECTED);
    }

    @Test
    void validate_offerToHired_passes() {
        validator.validateTransition(ApplicationStatus.OFFER, ApplicationStatus.HIRED);
    }

    @Test
    void validate_offerToRejected_passes() {
        validator.validateTransition(ApplicationStatus.OFFER, ApplicationStatus.REJECTED);
    }

    // ─── Terminal State Rejections ────────────────────────────────────────────

    @Test
    void validate_fromHired_throwsIllegalArgument() {
        assertThatThrownBy(() -> validator.validateTransition(ApplicationStatus.HIRED, ApplicationStatus.REVIEWING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("terminal state");
    }

    @Test
    void validate_fromRejected_throwsIllegalArgument() {
        assertThatThrownBy(() -> validator.validateTransition(ApplicationStatus.REJECTED, ApplicationStatus.REVIEWING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("terminal state");
    }

    @Test
    void validate_fromWithdrawn_throwsIllegalArgument() {
        assertThatThrownBy(() -> validator.validateTransition(ApplicationStatus.WITHDRAWN, ApplicationStatus.APPLIED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("terminal state");
    }

    // ─── Same Status ──────────────────────────────────────────────────────────

    @Test
    void validate_sameStatus_throwsIllegalArgument() {
        assertThatThrownBy(() -> validator.validateTransition(ApplicationStatus.APPLIED, ApplicationStatus.APPLIED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already");
    }

    // ─── Invalid Forward Jumps ────────────────────────────────────────────────

    @Test
    void validate_appliedToOffer_throwsIllegalArgument() {
        assertThatThrownBy(() -> validator.validateTransition(ApplicationStatus.APPLIED, ApplicationStatus.OFFER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid status transition");
    }

    @Test
    void validate_reviewingToHired_throwsIllegalArgument() {
        assertThatThrownBy(() -> validator.validateTransition(ApplicationStatus.REVIEWING, ApplicationStatus.HIRED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── isValidTransition helper ─────────────────────────────────────────────

    @Test
    void isValidTransition_validPair_returnsTrue() {
        assertThat(validator.isValidTransition(ApplicationStatus.APPLIED, ApplicationStatus.REVIEWING)).isTrue();
    }

    @Test
    void isValidTransition_invalidPair_returnsFalse() {
        assertThat(validator.isValidTransition(ApplicationStatus.HIRED, ApplicationStatus.APPLIED)).isFalse();
    }

    @Test
    void isValidTransition_sameStatus_returnsFalse() {
        assertThat(validator.isValidTransition(ApplicationStatus.INTERVIEW, ApplicationStatus.INTERVIEW)).isFalse();
    }
}
