package org.workfitai.cvservice.validation;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.workfitai.cvservice.errors.InvalidDataException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileValidatorTest {

    @Test
    void validate_passes_forValidPdf() {
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", new byte[] { 1 });

        assertThatCode(() -> FileValidator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    void validate_throwsInvalidDataException_whenFileNull() {
        assertThatThrownBy(() -> FileValidator.validate(null))
                .isInstanceOf(InvalidDataException.class);
    }

    @Test
    void validate_throwsInvalidDataException_whenFileEmpty() {
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> FileValidator.validate(file))
                .isInstanceOf(InvalidDataException.class);
    }

    @Test
    void validate_throwsInvalidDataException_whenFileExceedsMaxSize() {
        byte[] oversized = new byte[11 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", oversized);

        assertThatThrownBy(() -> FileValidator.validate(file))
                .isInstanceOf(InvalidDataException.class);
    }

    @Test
    void validate_throwsInvalidDataException_whenContentTypeNotPdf() {
        MockMultipartFile file = new MockMultipartFile("file", "resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", new byte[] { 1 });

        assertThatThrownBy(() -> FileValidator.validate(file))
                .isInstanceOf(InvalidDataException.class);
    }
}
