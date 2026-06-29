package org.workfitai.applicationservice.validation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.workfitai.applicationservice.dto.request.CreateApplicationRequest;
import org.workfitai.applicationservice.exception.FileStorageException;

class FileValidatorTest {

    private final FileValidator validator = new FileValidator();

    private MockMultipartFile pdf(String name, long sizeBytes) {
        return new MockMultipartFile("cv", name, "application/pdf",
                new byte[(int) sizeBytes]);
    }

    private CreateApplicationRequest req(MockMultipartFile file) {
        return CreateApplicationRequest.builder()
                .jobId("job-1").email("a@b.com").cvPdfFile(file).build();
    }

    // ─── valid file ───────────────────────────────────────────────────────────

    @Test
    void validate_validPdf_passes() {
        MockMultipartFile file = pdf("resume.pdf", 1024);
        assertThatCode(() -> validator.validate(req(file), "user1")).doesNotThrowAnyException();
    }

    // ─── null / empty file ────────────────────────────────────────────────────

    @Test
    void validate_nullFile_throws() {
        CreateApplicationRequest req = CreateApplicationRequest.builder()
                .jobId("job-1").email("a@b.com").cvPdfFile(null).build();

        assertThatThrownBy(() -> validator.validate(req, "user1"))
                .isInstanceOf(FileStorageException.class)
                .hasMessageContaining("required");
    }

    @Test
    void validate_emptyFile_throws() {
        MockMultipartFile empty = new MockMultipartFile("cv", "resume.pdf",
                "application/pdf", new byte[0]);

        assertThatThrownBy(() -> validator.validate(req(empty), "user1"))
                .isInstanceOf(FileStorageException.class)
                .hasMessageContaining("required");
    }

    // ─── wrong content type ───────────────────────────────────────────────────

    @Test
    void validate_nonPdfContentType_throws() {
        MockMultipartFile file = new MockMultipartFile("cv", "resume.docx",
                "application/msword", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> validator.validate(req(file), "user1"))
                .isInstanceOf(FileStorageException.class)
                .hasMessageContaining("Invalid file type");
    }

    // ─── file too large ───────────────────────────────────────────────────────

    @Test
    void validate_fileTooLarge_throws() {
        long oversized = 6 * 1024 * 1024; // 6 MB
        MockMultipartFile file = pdf("resume.pdf", oversized);

        assertThatThrownBy(() -> validator.validate(req(file), "user1"))
                .isInstanceOf(FileStorageException.class)
                .hasMessageContaining("exceeds");
    }

    @Test
    void validate_exactlyAtLimit_passes() {
        long exactly5Mb = 5 * 1024 * 1024;
        MockMultipartFile file = pdf("resume.pdf", exactly5Mb);
        assertThatCode(() -> validator.validate(req(file), "user1")).doesNotThrowAnyException();
    }

    // ─── filename validation ──────────────────────────────────────────────────

    @Test
    void validate_blankFilename_throws() {
        MockMultipartFile file = new MockMultipartFile("cv", "  ",
                "application/pdf", new byte[]{1});

        assertThatThrownBy(() -> validator.validate(req(file), "user1"))
                .isInstanceOf(FileStorageException.class)
                .hasMessageContaining("filename");
    }

    @Test
    void validate_nonPdfExtension_throws() {
        MockMultipartFile file = new MockMultipartFile("cv", "resume.PDF.exe",
                "application/pdf", new byte[]{1});

        assertThatThrownBy(() -> validator.validate(req(file), "user1"))
                .isInstanceOf(FileStorageException.class)
                .hasMessageContaining(".pdf extension");
    }

    @Test
    void validate_uppercasePdfExtension_passes() {
        MockMultipartFile file = new MockMultipartFile("cv", "RESUME.PDF",
                "application/pdf", new byte[]{1});
        assertThatCode(() -> validator.validate(req(file), "user1")).doesNotThrowAnyException();
    }

    // ─── getOrder ─────────────────────────────────────────────────────────────

    @Test
    void getOrder_returnsTwo() {
        org.assertj.core.api.Assertions.assertThat(validator.getOrder()).isEqualTo(2);
    }
}
