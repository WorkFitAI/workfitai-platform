package org.workfitai.authservice.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.workfitai.authservice.document.TwoFactorAuth;
import org.workfitai.authservice.dto.kafka.NotificationEvent;
import org.workfitai.authservice.dto.request.Disable2FARequest;
import org.workfitai.authservice.dto.request.Enable2FARequest;
import org.workfitai.authservice.dto.response.Enable2FAResponse;
import org.workfitai.authservice.model.User;
import org.workfitai.authservice.exception.BadRequestException;
import org.workfitai.authservice.exception.NotFoundException;
import org.workfitai.authservice.repository.TwoFactorAuthRepository;
import org.workfitai.authservice.repository.UserRepository;
import org.workfitai.authservice.service.NotificationProducer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TwoFactorAuthService {

    private final TwoFactorAuthRepository twoFactorAuthRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final GoogleAuthenticator googleAuthenticator;
    private final NotificationProducer notificationProducer;

    @Value("${app.frontend.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    private static final String ISSUER = "WorkFitAI";
    private static final int BACKUP_CODES_COUNT = 10;

    @Transactional
    public Enable2FAResponse enable2FA(String username, Enable2FARequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));

        // Check if 2FA is already enabled
        Optional<TwoFactorAuth> existing2FA = twoFactorAuthRepository.findByUserId(user.getId());
        if (existing2FA.isPresent() && existing2FA.get().getEnabled()) {
            throw new BadRequestException("Two-factor authentication is already enabled");
        }

        String method = request.getMethod();
        String secret = null;
        String qrCodeUrl = null;
        List<String> backupCodes = generateBackupCodes();

        if ("TOTP".equals(method)) {
            // Generate TOTP secret
            GoogleAuthenticatorKey key = googleAuthenticator.createCredentials();
            secret = key.getKey();

            // Generate QR code
            String otpAuthUrl = String.format(
                    "otpauth://totp/%s:%s?secret=%s&issuer=%s",
                    ISSUER, username, secret, ISSUER);

            qrCodeUrl = generateQRCodeDataUrl(otpAuthUrl);

        } else if ("EMAIL".equals(method)) {
            // For email-based 2FA, we don't need a secret
            secret = null;
            qrCodeUrl = null;
        } else {
            throw new BadRequestException("Invalid 2FA method. Use TOTP or EMAIL");
        }

        // Save 2FA configuration
        TwoFactorAuth twoFactorAuth = TwoFactorAuth.builder()
                .userId(user.getId())
                .method(method)
                .secret(secret)
                .backupCodes(hashBackupCodes(backupCodes))
                .enabled(true)
                .enabledAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        twoFactorAuthRepository.save(twoFactorAuth);

        // Send 2FA enabled notification
        send2FAEnabledNotification(user, method, backupCodes);

        log.info("Enabled 2FA for user: {} with method: {}", username, method);

        return Enable2FAResponse.builder()
                .method(method)
                .secret(secret)
                .qrCodeUrl(qrCodeUrl)
                .backupCodes(backupCodes)
                .message("Two-factor authentication enabled successfully")
                .build();
    }

    @Transactional
    public Map<String, String> disable2FA(String username, Disable2FARequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadRequestException("Invalid password");
        }

        TwoFactorAuth twoFactorAuth = twoFactorAuthRepository.findByUserId(user.getId())
                .orElseThrow(() -> new NotFoundException("Two-factor authentication is not enabled"));

        // Verify 2FA code
        if (!verify2FACode(twoFactorAuth, request.getCode())) {
            throw new BadRequestException("Invalid 2FA code");
        }

        // Delete 2FA configuration
        String previousMethod = twoFactorAuth.getMethod();
        twoFactorAuthRepository.delete(twoFactorAuth);

        // Send 2FA disabled notification
        send2FADisabledNotification(user, previousMethod);

        log.info("Disabled 2FA for user: {}", username);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Two-factor authentication disabled successfully");

        return response;
    }

    public boolean verify2FACode(TwoFactorAuth twoFactorAuth, String code) {
        if ("TOTP".equals(twoFactorAuth.getMethod())) {
            // Verify TOTP code
            return googleAuthenticator.authorize(twoFactorAuth.getSecret(), Integer.parseInt(code));

        } else if ("EMAIL".equals(twoFactorAuth.getMethod())) {
            // For email-based 2FA, verification would be done elsewhere
            // This is a placeholder
            return true;
        }

        return false;
    }

    public boolean verifyBackupCode(TwoFactorAuth twoFactorAuth, String code) {
        List<String> backupCodes = twoFactorAuth.getBackupCodes();
        if (backupCodes == null || backupCodes.isEmpty()) {
            return false;
        }

        for (String hashedCode : backupCodes) {
            if (passwordEncoder.matches(code, hashedCode)) {
                // Remove used backup code
                backupCodes.remove(hashedCode);
                twoFactorAuth.setBackupCodes(backupCodes);
                twoFactorAuth.setUpdatedAt(LocalDateTime.now());
                twoFactorAuthRepository.save(twoFactorAuth);
                return true;
            }
        }

        return false;
    }

    private List<String> generateBackupCodes() {
        List<String> codes = new ArrayList<>();
        Random random = new Random();

        for (int i = 0; i < BACKUP_CODES_COUNT; i++) {
            String code = String.format("%08d", random.nextInt(100000000));
            codes.add(code);
        }

        return codes;
    }

    private List<String> hashBackupCodes(List<String> codes) {
        List<String> hashedCodes = new ArrayList<>();
        for (String code : codes) {
            hashedCodes.add(passwordEncoder.encode(code));
        }
        return hashedCodes;
    }

    private String generateQRCodeDataUrl(String text) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, 200, 200);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);

            byte[] qrCodeBytes = outputStream.toByteArray();
            String base64QRCode = Base64.getEncoder().encodeToString(qrCodeBytes);

            return "data:image/png;base64," + base64QRCode;

        } catch (WriterException | IOException e) {
            log.error("Error generating QR code", e);
            throw new BadRequestException("Failed to generate QR code");
        }
    }

    private void send2FAEnabledNotification(User user, String method, List<String> backupCodes) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("username", user.getUsername());
            data.put("method", method);
            data.put("enabledAt", LocalDateTime.now().toString());
            data.put("destination", method.equals("EMAIL") ? user.getEmail() : "Authenticator App");
            data.put("backupCodes", backupCodes);
            data.put("settingsUrl", frontendBaseUrl + "/profile/security");
            data.put("downloadCodesUrl", frontendBaseUrl + "/profile/backup-codes");
            data.put("disableUrl", frontendBaseUrl + "/auth/disable-2fa");

            NotificationEvent event = NotificationEvent.builder()
                    .recipientEmail(user.getEmail())
                    .templateType("2fa-enabled")
                    .sendEmail(true)
                    .createInAppNotification(true)
                    .metadata(data)
                    .build();

            notificationProducer.send(event);
            log.info("Sent 2FA enabled notification to: {}", user.getEmail());

        } catch (Exception e) {
            log.error("Failed to send 2FA enabled notification for user: {}", user.getUsername(), e);
        }
    }

    private void send2FADisabledNotification(User user, String previousMethod) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("username", user.getUsername());
            data.put("disabledAt", LocalDateTime.now().toString());
            data.put("previousMethod", previousMethod);
            data.put("enableUrl", frontendBaseUrl + "/auth/enable-2fa");
            data.put("changePasswordUrl", frontendBaseUrl + "/auth/change-password");
            data.put("activityUrl", frontendBaseUrl + "/auth/sessions");

            NotificationEvent event = NotificationEvent.builder()
                    .recipientEmail(user.getEmail())
                    .templateType("2fa-disabled")
                    .sendEmail(true)
                    .createInAppNotification(true)
                    .metadata(data)
                    .build();

            notificationProducer.send(event);
            log.info("Sent 2FA disabled notification to: {}", user.getEmail());

        } catch (Exception e) {
            log.error("Failed to send 2FA disabled notification for user: {}", user.getUsername(), e);
        }
    }
}
