package org.workfitai.userservice.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imgscalr.Scalr;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.workfitai.userservice.config.CloudinaryConfig;
import org.workfitai.userservice.dto.response.AvatarResponse;
import org.workfitai.userservice.model.UserEntity;
import org.workfitai.userservice.exception.BadRequestException;
import org.workfitai.userservice.exception.NotFoundException;
import org.workfitai.userservice.repository.UserRepository;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
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

    @Transactional
    public AvatarResponse uploadAvatar(String username, MultipartFile file) {
        // Validate file
        validateFile(file);

        // Get user
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));

        try {
            // Delete old avatar if exists
            if (user.getAvatarPublicId() != null) {
                deleteFromCloudinary(user.getAvatarPublicId());
            }

            // Process and upload image
            byte[] processedImage = processImage(file);
            Map uploadResult = uploadToCloudinary(processedImage, username);

            String avatarUrl = (String) uploadResult.get("secure_url");
            String publicId = (String) uploadResult.get("public_id");

            // Update user entity
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
            log.error("Failed to upload avatar for user: {}", username, e);
            throw new BadRequestException("Failed to upload avatar: " + e.getMessage());
        }
    }

    @Transactional
    public void deleteAvatar(String username) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (user.getAvatarPublicId() == null) {
            throw new BadRequestException("No avatar to delete");
        }

        try {
            // Delete from Cloudinary
            deleteFromCloudinary(user.getAvatarPublicId());

            // Update user entity
            user.setAvatarUrl(null);
            user.setAvatarPublicId(null);
            user.setAvatarUploadedAt(null);
            userRepository.save(user);

            log.info("Avatar deleted successfully for user: {}", username);

        } catch (Exception e) {
            log.error("Failed to delete avatar for user: {}", username, e);
            throw new BadRequestException("Failed to delete avatar: " + e.getMessage());
        }
    }

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

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is required");
        }

        // Check file size
        if (file.getSize() > cloudinaryConfig.getMaxFileSize()) {
            throw new BadRequestException("File size exceeds maximum limit of 5MB");
        }

        // Check file type
        String contentType = file.getContentType();
        List<String> allowedFormats = Arrays.asList(cloudinaryConfig.getAllowedFormats().split(","));

        boolean isValidFormat = allowedFormats.stream()
                .anyMatch(format -> contentType != null &&
                        contentType.toLowerCase().contains(format.toLowerCase()));

        if (!isValidFormat) {
            throw new BadRequestException("Invalid file format. Allowed formats: " +
                    cloudinaryConfig.getAllowedFormats());
        }
    }

    private byte[] processImage(MultipartFile file) throws IOException {
        BufferedImage originalImage = ImageIO.read(file.getInputStream());

        if (originalImage == null) {
            throw new BadRequestException("Invalid image file");
        }

        // Resize image
        CloudinaryConfig.Transformation transform = cloudinaryConfig.getTransformation();
        BufferedImage resizedImage = Scalr.resize(
                originalImage,
                Scalr.Method.QUALITY,
                Scalr.Mode.FIT_EXACT,
                transform.getWidth(),
                transform.getHeight(),
                Scalr.OP_ANTIALIAS);

        // Convert to byte array
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String format = getImageFormat(file.getOriginalFilename());
        ImageIO.write(resizedImage, format, baos);

        return baos.toByteArray();
    }

    private Map uploadToCloudinary(byte[] imageData, String username) throws IOException {
        CloudinaryConfig.Transformation transform = cloudinaryConfig.getTransformation();

        Map uploadParams = ObjectUtils.asMap(
                "folder", cloudinaryConfig.getFolder(),
                "public_id", "avatar_" + username + "_" + System.currentTimeMillis(),
                "overwrite", true,
                "resource_type", "image",
                "transformation", Arrays.asList(
                        ObjectUtils.asMap(
                                "width", transform.getWidth(),
                                "height", transform.getHeight(),
                                "crop", transform.getCrop(),
                                "gravity", transform.getGravity(),
                                "quality", transform.getQuality())));

        return cloudinary.uploader().upload(imageData, uploadParams);
    }

    private void deleteFromCloudinary(String publicId) throws IOException {
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (Exception e) {
            throw new IOException("Failed to delete from Cloudinary", e);
        }
    }

    private String getImageFormat(String filename) {
        if (filename == null)
            return "jpg";

        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        switch (extension) {
            case "png":
                return "png";
            case "webp":
                return "webp";
            case "jpeg":
            case "jpg":
            default:
                return "jpg";
        }
    }
}
