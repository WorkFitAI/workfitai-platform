package org.workfitai.applicationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.io.InputStream;

import org.workfitai.applicationservice.config.MinioConfig;
import org.workfitai.applicationservice.dto.response.PreSignedUrlResponse;
import org.workfitai.applicationservice.exception.FileStorageException;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;

@ExtendWith(MockitoExtension.class)
class MinioPreSignedUrlServiceTest {

    @Mock MinioClient minioClient;
    @Mock MinioConfig minioConfig;

    @InjectMocks MinioPreSignedUrlService service;

    // ─── extractObjectKey ─────────────────────────────────────────────────────

    @Test
    void extractObjectKey_standardUrl_extractsKey() {
        when(minioConfig.getBucket()).thenReturn("cvs-files");

        String key = service.extractObjectKey("http://minio:9000/cvs-files/user1/app-1/cv.pdf");

        assertThat(key).isEqualTo("user1/app-1/cv.pdf");
    }

    @Test
    void extractObjectKey_urlWithoutBucketPrefix_throwsFileStorageException() {
        when(minioConfig.getBucket()).thenReturn("cvs-files");

        assertThatThrownBy(() -> service.extractObjectKey("http://minio:9000/other-bucket/file.pdf"))
                .isInstanceOf(FileStorageException.class)
                .hasMessageContaining("Invalid file URL format");
    }

    // ─── generateDownloadUrl ──────────────────────────────────────────────────

    @Test
    void generateDownloadUrl_success_returnsResponse() throws Exception {
        when(minioConfig.getBucket()).thenReturn("cvs-files");
        when(minioConfig.getExternalEndpoint()).thenReturn(null);
        when(minioClient.getPresignedObjectUrl(any())).thenReturn("http://minio:9000/cvs-files/user1/cv.pdf");

        PreSignedUrlResponse response = service.generateDownloadUrl("user1/cv.pdf", "cv.pdf", 1024L);

        assertThat(response.getUrl()).contains("minio:9000");
        assertThat(response.getFileName()).isEqualTo("cv.pdf");
        assertThat(response.getFileSize()).isEqualTo(1024L);
        assertThat(response.getExpiresAt()).isNotNull();
    }

    @Test
    void generateDownloadUrl_externalEndpointConfigured_replacesInternalUrl() throws Exception {
        when(minioConfig.getBucket()).thenReturn("cvs-files");
        when(minioConfig.getExternalEndpoint()).thenReturn("http://localhost:9000");
        when(minioConfig.getEndpoint()).thenReturn("http://minio:9000");
        when(minioClient.getPresignedObjectUrl(any()))
                .thenReturn("http://minio:9000/cvs-files/user1/cv.pdf?X-Amz-Signature=abc");

        PreSignedUrlResponse response = service.generateDownloadUrl("user1/cv.pdf", "cv.pdf", null);

        assertThat(response.getUrl()).startsWith("http://localhost:9000");
        assertThat(response.getUrl()).doesNotContain("minio:9000");
    }

    @Test
    void generateDownloadUrl_minioFails_throwsFileStorageException() throws Exception {
        when(minioConfig.getBucket()).thenReturn("cvs-files");
        when(minioClient.getPresignedObjectUrl(any()))
                .thenThrow(new RuntimeException("MinIO unreachable"));

        assertThatThrownBy(() -> service.generateDownloadUrl("user1/cv.pdf", "cv.pdf", null))
                .isInstanceOf(FileStorageException.class)
                .hasMessageContaining("Failed to generate download URL");
    }

    @Test
    void generateDownloadUrl_emptyExternalEndpoint_doesNotReplace() throws Exception {
        when(minioConfig.getBucket()).thenReturn("cvs-files");
        when(minioConfig.getExternalEndpoint()).thenReturn(""); // empty → skip replace branch
        when(minioClient.getPresignedObjectUrl(any())).thenReturn("http://minio:9000/cvs-files/key");

        PreSignedUrlResponse response = service.generateDownloadUrl("key", "cv.pdf", 512L);

        assertThat(response.getUrl()).contains("minio:9000");
    }

    // ─── downloadFile ─────────────────────────────────────────────────────────

    @Test
    void downloadFile_minioThrows_throwsFileStorageException() throws Exception {
        when(minioConfig.getBucket()).thenReturn("cvs-files");
        when(minioClient.getObject(any(GetObjectArgs.class)))
                .thenThrow(new RuntimeException("MinIO down"));

        assertThatThrownBy(() -> service.downloadFile("user1/cv.pdf"))
                .isInstanceOf(FileStorageException.class)
                .hasMessageContaining("Failed to download file");
    }

    // ─── downloadStream ───────────────────────────────────────────────────────

    @Test
    void downloadStream_minioThrows_throwsFileStorageException() throws Exception {
        when(minioConfig.getBucket()).thenReturn("cvs-files");
        when(minioClient.getObject(any(GetObjectArgs.class)))
                .thenThrow(new RuntimeException("MinIO down"));

        assertThatThrownBy(() -> service.downloadStream("user1/cv.pdf"))
                .isInstanceOf(FileStorageException.class)
                .hasMessageContaining("Failed to stream file");
    }
}
