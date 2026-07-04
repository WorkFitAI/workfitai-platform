package org.workfitai.jobservice.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudinaryServiceTest {

    @Mock
    private Cloudinary cloudinary;
    @Mock
    private Uploader uploader;
    @InjectMocks
    private CloudinaryService cloudinaryService;

    @Test
    void uploadFile_returnsSecureUrl() throws IOException {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), any())).thenReturn(Map.of("secure_url", "https://cdn/file.png"));
        var file = new MockMultipartFile("file", "file.png", "image/png", new byte[] { 1, 2, 3 });

        String url = cloudinaryService.uploadFile(file, "logos");

        assertThat(url).isEqualTo("https://cdn/file.png");
    }

    @Test
    void deleteFile_returnsDestroyResultAsString() throws IOException {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.destroy(any(), any())).thenReturn(Map.of("result", "ok"));

        String result = cloudinaryService.deleteFile("public-id-123");

        assertThat(result).contains("ok");
    }
}
