package org.workfitai.applicationservice.validation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workfitai.applicationservice.dto.request.CreateApplicationRequest;
import org.workfitai.applicationservice.exception.ApplicationConflictException;
import org.workfitai.applicationservice.repository.ApplicationRepository;

@ExtendWith(MockitoExtension.class)
class DuplicateApplicationValidatorTest {

    @Mock ApplicationRepository applicationRepository;
    @InjectMocks DuplicateApplicationValidator validator;

    private final CreateApplicationRequest request = CreateApplicationRequest.builder()
            .jobId("job-1")
            .email("c@example.com")
            .build();

    @Test
    void validate_noDuplicate_passes() {
        when(applicationRepository.existsByUsernameAndJobIdAndDeletedAtIsNull("user1", "job-1"))
                .thenReturn(false);

        validator.validate(request, "user1");

        verify(applicationRepository).existsByUsernameAndJobIdAndDeletedAtIsNull("user1", "job-1");
    }

    @Test
    void validate_duplicateExists_throwsConflict() {
        when(applicationRepository.existsByUsernameAndJobIdAndDeletedAtIsNull("user1", "job-1"))
                .thenReturn(true);

        assertThatThrownBy(() -> validator.validate(request, "user1"))
                .isInstanceOf(ApplicationConflictException.class);
    }

    @Test
    void getOrder_returnsOne() {
        org.assertj.core.api.Assertions.assertThat(validator.getOrder()).isEqualTo(1);
    }
}
