package org.workfitai.cvservice.service.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.workfitai.cvservice.constant.CVConst;

class FileServiceTest {

    private final MinioClient minioClient = mock(MinioClient.class);
    private final FileService fileService = new FileService(minioClient);

    @Test
    void uploadCV_sanitizesDisplayNameContainingSpacesAndSpecialChars() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", new byte[] { 1 });

        String objectName = fileService.uploadCV(file, "Senior Backend Developer (Java/Spring)!");

        assertThat(objectName).matches(CVConst.PDF_FILE_PATTERN);
        assertThat(objectName).endsWith("-Senior_Backend_Developer__Java_Spring__.pdf");
        org.mockito.Mockito.verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void uploadCV_fallsBackToDefaultName_whenDisplayNameBlank() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", new byte[] { 1 });

        String objectName = fileService.uploadCV(file, "   ");

        assertThat(objectName).matches(CVConst.PDF_FILE_PATTERN);
        assertThat(objectName).endsWith("-cv.pdf");
    }

    @Test
    void uploadCV_singleArgOverload_sanitizesOriginalFilenameWithSpaces() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "My Resume (final).pdf", "application/pdf", new byte[] { 1 });

        String objectName = fileService.uploadCV(file);

        assertThat(objectName).matches(CVConst.PDF_FILE_PATTERN);
    }
}
