package org.workfitai.cvservice.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workfitai.cvservice.repository.CVRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CvIdValidatorTest {

    @Mock
    private CVRepository cvRepository;

    private CvIdValidator validator;

    private void init() {
        validator = new CvIdValidator(cvRepository);
    }

    @Test
    void isValid_returnsFalse_whenCvIdNull() {
        init();

        assertThat(validator.isValid(null, null)).isFalse();
    }

    @Test
    void isValid_returnsTrue_whenCvIdDoesNotExist() {
        init();
        when(cvRepository.existsById("new-id")).thenReturn(false);

        assertThat(validator.isValid("new-id", null)).isTrue();
    }

    @Test
    void isValid_returnsFalse_whenCvIdAlreadyExists() {
        init();
        when(cvRepository.existsById("existing-id")).thenReturn(true);

        assertThat(validator.isValid("existing-id", null)).isFalse();
    }
}
