package org.workfitai.cvservice.service.shared;

import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@AllArgsConstructor
public class FileService {

    private final MinioClient minioClient;
    private final String bucket = "cvs-files";

    public String uploadCV(MultipartFile file) throws Exception {
        return uploadCV(file, file.getOriginalFilename());
    }

    /**
     * Uploads a CV PDF, naming the MinIO object after {@code displayName} instead of the
     * raw original filename (e.g. the job title for an application snapshot).
     *
     * The name is sanitized to satisfy {@link org.workfitai.cvservice.constant.CVConst#PDF_FILE_PATTERN}
     * — downloadCV() rejects anything that doesn't match, so whatever we store here MUST match it too.
     */
    public String uploadCV(MultipartFile file, String displayName) throws Exception {
        String objectName = UUID.randomUUID() + "-" + sanitizeFileNameSegment(displayName) + ".pdf";

        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .stream(file.getInputStream(), file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build()
        );

        return objectName; // chính là storageKey bạn lưu DB
    }

    /**
     * Strips the extension (if any) and replaces every character outside [a-zA-Z0-9._-]
     * with "_" so the result is always safe to embed in a MinIO object key and a download
     * Content-Disposition header.
     */
    private String sanitizeFileNameSegment(String rawName) {
        String base = rawName == null || rawName.isBlank() ? "cv" : rawName;
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        String sanitized = base.replaceAll("[^a-zA-Z0-9._-]", "_");
        return sanitized.isBlank() ? "cv" : sanitized;
    }

    public InputStream downloadCV(String objectName) throws Exception {
        return minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .build()
        );
    }

    public String generateFileUrl(String objectName) throws Exception {
        return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(bucket)
                        .object(objectName)
                        .expiry(7, TimeUnit.DAYS)
                        .build()
        );
    }
}
