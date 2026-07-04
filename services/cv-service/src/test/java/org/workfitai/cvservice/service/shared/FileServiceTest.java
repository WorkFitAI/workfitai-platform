package org.workfitai.cvservice.service.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
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

    @Test
    void uploadCV_replacesEachDisallowedCharacterWithUnderscore() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", new byte[] { 1 });

        String objectName = fileService.uploadCV(file, "???");

        assertThat(objectName).matches(CVConst.PDF_FILE_PATTERN);
        assertThat(objectName).endsWith("-___.pdf");
    }

    @Test
    void uploadCV_keepsWholeNameUnchanged_whenDotIsFirstCharacter() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", new byte[] { 1 });

        String objectName = fileService.uploadCV(file, ".hidden");

        assertThat(objectName).endsWith("-.hidden.pdf");
    }

    @Test
    void uploadCV_fallsBackToDefaultName_whenDisplayNameNull() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", new byte[] { 1 });

        String objectName = fileService.uploadCV(file, null);

        assertThat(objectName).endsWith("-cv.pdf");
    }

    @Test
    void downloadCV_returnsStreamFromMinio() throws Exception {
        java.io.InputStream stream = new java.io.ByteArrayInputStream(new byte[] { 1, 2, 3 });
        io.minio.GetObjectResponse minioResponse = mock(io.minio.GetObjectResponse.class);
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(minioResponse);

        java.io.InputStream result = fileService.downloadCV("some-object.pdf");

        assertThat(result).isEqualTo(minioResponse);
    }

    @Test
    void generateFileUrl_returnsPresignedUrl() throws Exception {
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://minio/cvs-files/some-object.pdf?signed=true");

        String url = fileService.generateFileUrl("some-object.pdf");

        assertThat(url).isEqualTo("http://minio/cvs-files/some-object.pdf?signed=true");
    }

    @Test
    void uploadCvBytes_sanitizesDisplayNameAndUploadsRawBytes() throws Exception {
        java.io.InputStream content = new java.io.ByteArrayInputStream(new byte[] { 1, 2, 3 });

        String objectName = fileService.uploadCvBytes(content, 3L, "application/pdf", "Senior Backend Developer!");

        assertThat(objectName).matches(CVConst.PDF_FILE_PATTERN);
        assertThat(objectName).endsWith("-Senior_Backend_Developer_.pdf");
        org.mockito.Mockito.verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void uploadCvBytes_fallsBackToDefaultName_whenDisplayNameNull() throws Exception {
        java.io.InputStream content = new java.io.ByteArrayInputStream(new byte[] { 1 });

        String objectName = fileService.uploadCvBytes(content, 1L, "application/pdf", null);

        assertThat(objectName).endsWith("-cv.pdf");
    }
}
