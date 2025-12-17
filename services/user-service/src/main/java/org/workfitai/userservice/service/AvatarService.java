package org.workfitai.userservice.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.workfitai.userservice.config.CloudinaryConfig;
import org.workfitai.userservice.dto.response.AvatarResponse;
import org.workfitai.userservice.exception.BadRequestException;
import org.workfitai.userservice.exception.NotFoundException;
import org.workfitai.userservice.model.UserEntity;
import org.workfitai.userservice.repository.UserRepository;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RefreshScope
@RequiredArgsConstructor
public class AvatarService {

    private final Cloudinary cloudinary;
    private final CloudinaryConfig cloudinaryConfig;
    private final UserRepository userRepository;

    /*
     * =========================
     * Upload avatar
     * =========================
     */
    @Transactional
    public AvatarResponse uploadAvatar(String username, MultipartFile file) {
        validateFile(file);

        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));

        try {
            // Remove old avatar if exists
            if (user.getAvatarPublicId() != null) {
                deleteFromCloudinary(user.getAvatarPublicId());
            }

            // Upload to Cloudinary (Cloudinary handles resize/crop/face)
            Map uploadResult = uploadToCloudinary(file.getBytes(), username);

            String avatarUrl = (String) uploadResult.get("secure_url");
            String publicId = (String) uploadResult.get("public_id");

            user.setAvatarUrl(avatarUrl);
            user.setAvatarPublicId(publicId);
            user.setAvatarUploadedAt(Instant.now());
            userRepository.save(user);

            log.info("Avatar uploaded successfully for user: {}", username);

            return AvatarResponse.builder()
                    .avatarUrl(avatarUrl)
                    .publicId(publicId)
                    .uploadedAt(user.getAvatarUploadedAt())
                    .message("Avatar uploaded successfully")
                    .build();

        } catch (IOException e) {
            log.error("Avatar upload failed for user {}", username, e);
            throw new BadRequestException("Failed to upload avatar");
        }
    }

    /*
     * =========================
     * Delete avatar
     * =========================
     */
    @Transactional
    public void deleteAvatar(String username) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (user.getAvatarPublicId() == null) {
            throw new BadRequestException("No avatar to delete");
        }

        try {
            deleteFromCloudinary(user.getAvatarPublicId());

            user.setAvatarUrl(null);
            user.setAvatarPublicId(null);
            user.setAvatarUploadedAt(null);
            userRepository.save(user);

            log.info("Avatar deleted successfully for user: {}", username);

        } catch (IOException e) {
            log.error("Avatar delete failed for user {}", username, e);
            throw new BadRequestException("Failed to delete avatar");
        }
    }

    /*
     * =========================
     * Get avatar
     * =========================
     */
    public AvatarResponse getAvatar(String username) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (user.getAvatarUrl() == null) {
            throw new NotFoundException("No avatar found");
        }

        return AvatarResponse.builder()
                .avatarUrl(user.getAvatarUrl())
                .publicId(user.getAvatarPublicId())
                .uploadedAt(user.getAvatarUploadedAt())
                .build();
    }

    /*
     * =========================
     * Validation
     * =========================
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is required");
        }

        if (file.getSize() > cloudinaryConfig.getMaxFileSize()) {
            throw new BadRequestException("File size exceeds maximum limit");
        }

        String contentType = file.getContentType();
        List<String> allowedFormats = Arrays.asList(cloudinaryConfig.getAllowedFormats().split(","));

        boolean valid = allowedFormats.stream()
                .anyMatch(f -> contentType != null && contentType.toLowerCase().contains(f));

        if (!valid) {
            throw new BadRequestException(
                    "Invalid file format. Allowed: " + cloudinaryConfig.getAllowedFormats());
        }
    }

    /*
     * =========================
     * Cloudinary upload
     * =========================
     */
    private Map uploadToCloudinary(byte[] imageData, String username) throws IOException {
        CloudinaryConfig.Transformation cfg = cloudinaryConfig.getTransformation();

        // ✅ CORRECT Cloudinary Java SDK usage
        Transformation transformation = new Transformation()
                .crop(cfg.getCrop()) // e.g. fill
                .gravity(cfg.getGravity()) // face
                .width(cfg.getWidth()) // 256
                .height(cfg.getHeight()) // 256
                .quality(cfg.getQuality()); // auto

        Map<String, Object> uploadParams = ObjectUtils.asMap(
                "folder", cloudinaryConfig.getFolder() + "/" + username,
                "public_id", "avatar_" + System.currentTimeMillis(),
                "overwrite", true,
                "resource_type", "image",
                "transformation", transformation);

        log.debug("Cloudinary transformation: {}", transformation.generate());
        return cloudinary.uploader().upload(imageData, uploadParams);
    }

    /*
     * =========================
     * Cloudinary delete
     * =========================
     */
    private void deleteFromCloudinary(String publicId) throws IOException {
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (Exception e) {
            throw new IOException("Failed to delete from Cloudinary", e);
        }
    }
}
