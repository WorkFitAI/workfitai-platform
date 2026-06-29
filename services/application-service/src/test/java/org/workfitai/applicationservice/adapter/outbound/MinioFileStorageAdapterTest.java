package org.workfitai.applicationservice.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.workfitai.applicationservice.config.MinioConfig;
import org.workfitai.applicationservice.dto.FileUploadResult;
import org.workfitai.applicationservice.exception.FileStorageException;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;

@ExtendWith(MockitoExtension.class)
class MinioFileStorageAdapterTest {

    @Mock MinioClient minioClient;
    @Mock MinioConfig minioConfig;

    @InjectMocks MinioFileStorageAdapter adapter;

    private MockMultipartFile validPdf() {
        return new MockMultipartFile(
                "file", "resume.pdf", "application/pdf",
                new byte[1024]);
    }

    // ─── init ─────────────────────────────────────────────────────────────────

    @Test
    void init_bucketDoesNotExist_createsBucket() throws Exception {
        when(minioConfig.getBucket()).thenReturn("cv-files");
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        adapter.init();

        verify(minioClient).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    void init_bucketAlreadyExists_skipsCreation() throws Exception {
        when(minioConfig.getBucket()).thenReturn("cv-files");
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        adapter.init(); // should not throw
    }

    @Test
    void init_minioThrows_doesNotFailStartup() throws Exception {
        when(minioConfig.getBucket()).thenReturn("cv-files");
        when(minioClient.bucketExists(any(BucketExistsArgs.class)))
                .thenThrow(new RuntimeException("MinIO unreachable"));

        adapter.init(); // must not throw — startup must survive
    }

    // ─── uploadFile ───────────────────────────────────────────────────────────

    @Test
    void uploadFile_validPdf_returnsUploadResult() throws Exception {
        when(minioConfig.getBucket()).thenReturn("cv-files");
        when(minioConfig.getEndpoint()).thenReturn("http://minio:9000");
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(null);

        FileUploadResult result = adapter.uploadFile(validPdf(), "user1", "app-1");

        assertThat(result).isNotNull();
        assertThat(result.getFileName()).isEqualTo("resume.pdf");
        assertThat(result.getContentType()).isEqualTo("application/pdf");
        assertThat(result.getFileUrl()).contains("cv-files");
        assertThat(result.getFileUrl()).contains("user1");
    }

    @Test
    void uploadFile_nullFile_throwsFileStorageException() {
        assertThatThrownBy(() -> adapter.uploadFile(null, "user1", "app-1"))
                .isInstanceOf(FileStorageException.class)
                .hasMessageContaining("empty or null");
    }

    @Test
    void uploadFile_wrongContentType_throwsFileStorageException() {
        MockMultipartFile wrongType = new MockMultipartFile(
                "file", "doc.docx", "application/msword", new byte[100]);

        assertThatThrownBy(() -> adapter.uploadFile(wrongType, "user1", "app-1"))
                .isInstanceOf(FileStorageException.class)
                .hasMessageContaining("PDF");
    }

    @Test
    void uploadFile_tooLarge_throwsFileStorageException() {
        // 6MB > 5MB limit
        MockMultipartFile bigFile = new MockMultipartFile(
                "file", "big.pdf", "application/pdf", new byte[6 * 1024 * 1024]);

        assertThatThrownBy(() -> adapter.uploadFile(bigFile, "user1", "app-1"))
                .isInstanceOf(FileStorageException.class)
                .hasMessageContaining("5MB");
    }

    @Test
    void uploadFile_minioThrows_throwsFileStorageException() throws Exception {
        when(minioConfig.getBucket()).thenReturn("cv-files");
        when(minioClient.putObject(any(PutObjectArgs.class)))
                .thenThrow(new RuntimeException("MinIO error"));

        assertThatThrownBy(() -> adapter.uploadFile(validPdf(), "user1", "app-1"))
                .isInstanceOf(FileStorageException.class)
                .hasMessageContaining("Failed to upload");
    }

    // ─── deleteFile ───────────────────────────────────────────────────────────

    @Test
    void deleteFile_validUrl_removesObject() throws Exception {
        when(minioConfig.getBucket()).thenReturn("cv-files");

        adapter.deleteFile("http://minio:9000/cv-files/user1/app1/file.pdf");

        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    void deleteFile_invalidUrl_throwsFileStorageException() {
        when(minioConfig.getBucket()).thenReturn("cv-files");

        assertThatThrownBy(() -> adapter.deleteFile("http://minio:9000/other-bucket/file.pdf"))
                .isInstanceOf(FileStorageException.class)
                .hasMessageContaining("Invalid file URL");
    }

    @Test
    void deleteFile_minioThrows_throwsFileStorageException() throws Exception {
        when(minioConfig.getBucket()).thenReturn("cv-files");
        doThrow(new RuntimeException("MinIO error"))
                .when(minioClient).removeObject(any(RemoveObjectArgs.class));

        assertThatThrownBy(() -> adapter.deleteFile("http://minio:9000/cv-files/user1/file.pdf"))
                .isInstanceOf(FileStorageException.class)
                .hasMessageContaining("Failed to delete");
    }

    // ─── fileExists ───────────────────────────────────────────────────────────

    @Test
    void fileExists_objectFound_returnsTrue() throws Exception {
        when(minioConfig.getBucket()).thenReturn("cv-files");
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(null);

        assertThat(adapter.fileExists("http://minio:9000/cv-files/user1/file.pdf")).isTrue();
    }

    @Test
    void fileExists_objectNotFound_returnsFalse() throws Exception {
        when(minioConfig.getBucket()).thenReturn("cv-files");
        when(minioClient.statObject(any(StatObjectArgs.class)))
                .thenThrow(new RuntimeException("Not found"));

        assertThat(adapter.fileExists("http://minio:9000/cv-files/user1/file.pdf")).isFalse();
    }
}
