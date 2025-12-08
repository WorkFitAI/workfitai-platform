package org.workfitai.authservice.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.workfitai.authservice.client.UserServiceClient;
import org.workfitai.authservice.constants.Messages;
import org.workfitai.authservice.dto.IssuedTokens;
import org.workfitai.authservice.dto.LoginRequest;
import org.workfitai.authservice.dto.PendingRegistration;
import org.workfitai.authservice.dto.RegisterRequest;
import org.workfitai.authservice.dto.VerifyOtpRequest;
import org.workfitai.authservice.dto.kafka.NotificationEvent;
import org.workfitai.authservice.dto.kafka.UserRegistrationEvent;
import org.workfitai.authservice.enums.UserRole;
import org.workfitai.authservice.enums.UserStatus;
import org.workfitai.authservice.model.User;
import org.workfitai.authservice.repository.UserRepository;
import org.workfitai.authservice.security.JwtService;
import org.workfitai.authservice.service.NotificationProducer;
import org.workfitai.authservice.service.OtpService;
import org.workfitai.authservice.service.RefreshTokenService;
import org.workfitai.authservice.service.UserRegistrationProducer;
import org.workfitai.authservice.service.iAuthService;

import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements iAuthService {

    private final UserRepository users;
    private final BCryptPasswordEncoder encoder;
    private final AuthenticationManager authManager;
    private final JwtService jwt;
    private final RefreshTokenService refreshStore;
    private final UserRegistrationProducer userRegistrationProducer;
    private final OtpService otpService;
    private final NotificationProducer notificationProducer;
    private final UserServiceClient userServiceClient;

    private static final String DEFAULT_DEVICE = Messages.Misc.DEFAULT_DEVICE;

    @Override
    public void register(RegisterRequest req) {
        UserRole role = req.getRole() == null ? UserRole.CANDIDATE : req.getRole();
        if (role == UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Admin accounts cannot be registered through this endpoint");
        }

        // Validate role-specific payload
        if ((role == UserRole.HR || role == UserRole.HR_MANAGER) && req.getHrProfile() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "HR profile is required for HR/HR_MANAGER registration");
        }
        if (role == UserRole.HR_MANAGER && req.getCompany() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Company information is required for HR_MANAGER registration");
        }
        // HR role must have hrManagerEmail (they will be assigned to HR Manager's
        // company)
        if (role == UserRole.HR && req.getHrProfile() != null) {
            String hrManagerEmail = req.getHrProfile().getHrManagerEmail();
            if (hrManagerEmail == null || hrManagerEmail.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "HR Manager email is required for HR registration. Please provide the email of your HR Manager.");
            }
        }
        if (role == UserRole.HR_MANAGER && req.getHrProfile() != null && req.getCompany() != null) {
            // Check if HR_MANAGER already exists for this company
            validateHRManagerUniqueness(req.getCompany().getName());
        }

        // Check if email already exists in auth-service
        Optional<User> existingUser = users.findByEmail(req.getEmail());
        if (existingUser.isPresent()) {
            User user = existingUser.get();
            // If user is PENDING (not verified OTP yet), resend OTP
            if (user.getStatus() == UserStatus.PENDING) {
                log.info("Resending OTP for pending user: {}", req.getEmail());
                resendOtpForPendingUser(user, req);
                return; // Exit early - OTP resent
            }
            // User already active or in other status
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is already in use");
        }

        // Check if email already exists in user-service (cross-service validation)
        try {
            Boolean existsInUserService = userServiceClient.existsByEmail(req.getEmail());
            if (Boolean.TRUE.equals(existsInUserService)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Email is already registered in the system");
            }
        } catch (ResponseStatusException e) {
            throw e; // Re-throw our validation errors
        } catch (Exception e) {
            log.warn("Could not validate email with user-service: {}. Proceeding with registration.", e.getMessage());
            // Continue with registration - will be caught by Kafka consumer if duplicate
        }

        // Generate unique username from email (part before @)
        String username = generateUniqueUsername(req.getEmail());

        String encodedPassword = encoder.encode(req.getPassword());

        // Generate and send OTP
        String otp = otpService.generateOtp();
        otpService.saveOtp(req.getEmail(), otp);

        // Store pending registration data in Redis for later use during OTP
        // verification
        PendingRegistration pendingData = PendingRegistration.builder()
                .email(req.getEmail())
                .username(username)
                .fullName(req.getFullName())
                .phoneNumber(req.getPhoneNumber())
                .passwordHash(encodedPassword)
                .role(role)
                .hrProfile(req.getHrProfile())
                .company(req.getCompany())
                .build();

        otpService.savePendingRegistration(req.getEmail(), pendingData);

        var user = User.builder()
                .username(username)
                .email(req.getEmail())
                .password(encodedPassword)
                .roles(Set.of(role.getRoleName()))
                .status(UserStatus.PENDING)
                .company(role == UserRole.HR_MANAGER && req.getCompany() != null ? req.getCompany().getName() : null)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        users.save(user);

        // Send OTP email
        sendOtpEmail(req.getEmail(), otp, req.getFullName());
    }

    /**
     * Resend OTP for a pending user (user who registered but didn't verify OTP).
     * Updates user info if changed and sends new OTP.
     */
    private void resendOtpForPendingUser(User existingUser, RegisterRequest req) {
        UserRole role = req.getRole() == null ? UserRole.CANDIDATE : req.getRole();

        // Update user info if they provided new data
        String newPasswordHash = encoder.encode(req.getPassword());
        existingUser.setPassword(newPasswordHash); // User model uses 'password' field
        existingUser.setUpdatedAt(Instant.now());
        users.save(existingUser);

        // Generate new OTP
        String otp = otpService.generateOtp();
        otpService.saveOtp(req.getEmail(), otp);

        // Update pending registration data
        PendingRegistration pendingData = PendingRegistration.builder()
                .email(req.getEmail())
                .username(existingUser.getUsername())
                .fullName(req.getFullName())
                .phoneNumber(req.getPhoneNumber())
                .passwordHash(newPasswordHash)
                .role(role)
                .hrProfile(req.getHrProfile())
                .company(req.getCompany())
                .build();
        otpService.savePendingRegistration(req.getEmail(), pendingData);

        // Send OTP email
        sendOtpEmail(req.getEmail(), otp, req.getFullName());

        log.info("OTP resent for pending user: {}", req.getEmail());
    }

    @Override
    public IssuedTokens login(LoginRequest req, String deviceId) {
        try {
            Authentication authentication = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            req.getUsernameOrEmail(), req.getPassword()));
            UserDetails ud = (UserDetails) authentication.getPrincipal();

            // Look up the user by username and check status
            User user = users.findByUsername(ud.getUsername())
                    .orElseThrow(
                            () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, Messages.Error.USER_NOT_FOUND));

            // Check if user is active
            if (user.getStatus() != UserStatus.ACTIVE) {
                String message = switch (user.getStatus()) {
                    case PENDING -> "Please verify your email before logging in.";
                    case WAIT_APPROVED -> "Your account is pending approval. Please wait for administrator approval.";
                    case INACTIVE -> "Your account has been deactivated.";
                    default -> "Your account is not active.";
                };
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
            }

            String access = jwt.generateAccessToken(ud);
            String jti = jwt.newJti();
            String refresh = jwt.generateRefreshTokenWithJti(ud, jti);

            String dev = normalizeDevice(deviceId);
            refreshStore.saveJti(user.getId(), dev, jti); // overwrite any previous jti for this device
            refreshStore.saveJti(user.getId(), dev, jti); // overwrite any previous jti for this device

            Set<String> roles = user.getRoles() != null ? user.getRoles() : Set.of();
            return IssuedTokens.of(access, refresh, jwt.getAccessExpMs(), user.getUsername(), roles);

        } catch (BadCredentialsException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, Messages.Error.INVALID_CREDENTIALS);
        }
    }

    @Override
    public void logout(String deviceId, String username) {
        String userId = users.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, Messages.Error.USER_NOT_FOUND))
                .getId();
        refreshStore.delete(userId, normalizeDevice(deviceId));
    }

    @Override
    public IssuedTokens refresh(String refreshTokenFromCookie, String deviceId) {
        String dev = normalizeDevice(deviceId);

        // Parse claims (throws on bad signature/expiry)
        final String username;
        final String jti;
        try {
            username = jwt.extractUsername(refreshTokenFromCookie);
            jti = jwt.extractJti(refreshTokenFromCookie);
        } catch (JwtException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, Messages.Error.TOKEN_INVALID);
        }

        String userId = users.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, Messages.Error.USER_NOT_FOUND))
                .getId();

        // Check device-scoped JTI in Redis
        String activeJti = refreshStore.getJti(userId, dev);
        if (activeJti == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, Messages.Error.TOKEN_EXPIRED);
        if (!activeJti.equals(jti))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, Messages.Error.TOKEN_INVALID);

        // Rotate
        User user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, Messages.Error.USER_NOT_FOUND));

        UserDetails ud = org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())
                .authorities(user.getRoles().toArray(new String[0]))
                .build();

        String access = jwt.generateAccessToken(ud);
        String newJti = jwt.newJti();
        String newRefresh = jwt.generateRefreshTokenWithJti(ud, newJti);

        refreshStore.saveJti(userId, dev, newJti); // overwrites + resets TTL

        return IssuedTokens.of(access, newRefresh, jwt.getAccessExpMs());
    }

    @Override
    public void verifyOtp(VerifyOtpRequest req) {
        if (!otpService.verifyOtp(req.getEmail(), req.getOtp())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired OTP");
        }

        // Retrieve pending registration data
        PendingRegistration pendingData = otpService.getPendingRegistration(req.getEmail());
        if (pendingData == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pending registration data not found");
        }

        User user = users.findByEmail(req.getEmail())
                .orElseGet(() -> createUserFromPending(pendingData));

        if (user.getStatus() != UserStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not in pending status");
        }

        UserRole role = UserRole.fromString(user.getRoles().iterator().next());

        // Determine target status based on role
        UserStatus targetStatus = (role == UserRole.CANDIDATE) ? UserStatus.ACTIVE : UserStatus.WAIT_APPROVED;

        // IMPORTANT: Publish to user-service FIRST (synchronous)
        // If this fails, we don't update auth-service status - ensures consistency
        try {
            publishUserRegistrationEvent(user, pendingData, targetStatus);
        } catch (Exception ex) {
            log.error("Failed to sync user {} to user-service: {}", req.getEmail(), ex.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Registration sync failed. Please try again later.");
        }

        // Only update status after successful sync to user-service
        user.setStatus(targetStatus);
        user.setUpdatedAt(Instant.now());
        users.save(user);

        // Clean up pending registration data
        otpService.deletePendingRegistration(req.getEmail());

        // Send appropriate notification
        if (role == UserRole.CANDIDATE) {
            sendAccountActivatedEmail(user.getEmail(), user.getUsername());
        } else {
            sendPendingApprovalEmail(user.getEmail(), user.getUsername(), role);
        }
    }

    private void publishUserRegistrationEvent(User user, PendingRegistration pendingData, UserStatus status) {
        UserRegistrationEvent event = UserRegistrationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("USER_REGISTERED")
                .timestamp(Instant.now())
                .userData(UserRegistrationEvent.UserData.builder()
                        .userId(user.getId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .fullName(pendingData.getFullName())
                        .phoneNumber(pendingData.getPhoneNumber())
                        .passwordHash(user.getPassword())
                        .role(pendingData.getRole().getRoleName())
                        .status(status.getStatusName())
                        .hrProfile(buildHrProfile(pendingData))
                        .company(buildCompanyData(pendingData))
                        .build())
                .build();

        userRegistrationProducer.publishUserRegistrationEvent(event);
    }

    private UserRegistrationEvent.HrProfile buildHrProfile(PendingRegistration pendingData) {
        if (pendingData.getHrProfile() == null) {
            return null;
        }

        return UserRegistrationEvent.HrProfile.builder()
                .department(pendingData.getHrProfile().getDepartment())
                .hrManagerEmail(pendingData.getHrProfile().getHrManagerEmail())
                .address(pendingData.getHrProfile().getAddress())
                .build();
    }

    private UserRegistrationEvent.CompanyData buildCompanyData(PendingRegistration pendingData) {
        if (pendingData.getCompany() == null) {
            return null;
        }

        return UserRegistrationEvent.CompanyData.builder()
                .name(pendingData.getCompany().getName())
                .logoUrl(pendingData.getCompany().getLogoUrl())
                .websiteUrl(pendingData.getCompany().getWebsiteUrl())
                .description(pendingData.getCompany().getDescription())
                .address(pendingData.getCompany().getAddress())
                .size(pendingData.getCompany().getSize())
                .build();
    }

    private void sendOtpEmail(String email, String otp, String fullName) {
        notificationProducer.send(NotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("EMAIL_NOTIFICATION")
                .recipientEmail(email)
                .recipientRole("USER")
                .templateType("OTP_VERIFICATION")
                .subject("Verify your email - WorkFitAI")
                .content("Your verification code is: " + otp)
                .metadata(Map.of(
                        "otp", otp,
                        "fullName", fullName,
                        "validUntil", "24 hours",
                        "loginUrl", "https://workfitai.com/login"))
                .build());
    }

    private void sendAccountActivatedEmail(String email, String username) {
        notificationProducer.send(NotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("EMAIL_NOTIFICATION")
                .recipientEmail(email)
                .recipientRole("CANDIDATE")
                .templateType("ACCOUNT_ACTIVATED")
                .subject("Welcome to WorkFitAI - Account Activated")
                .content("Your candidate account is now active!")
                .metadata(Map.of(
                        "username", username,
                        "role", "Candidate",
                        "loginUrl", "https://workfitai.com/login",
                        "isCandidate", "true"))
                .build());
    }

    private void sendPendingApprovalEmail(String email, String username, UserRole role) {
        String approverType = (role == UserRole.HR_MANAGER) ? "Administrator" : "HR Manager";

        notificationProducer.send(NotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("EMAIL_NOTIFICATION")
                .recipientEmail(email)
                .recipientRole(role.getRoleName())
                .templateType("PENDING_APPROVAL")
                .subject("Email Verified - Pending Approval")
                .content(
                        "Your email has been verified. Your account is now pending approval from " + approverType + ".")
                .metadata(Map.of(
                        "username", username,
                        "role", role.getRoleName(),
                        "approverType", approverType,
                        "loginUrl", "https://workfitai.com/login"))
                .build());
    }

    private User createUserFromPending(PendingRegistration pendingData) {
        UserRole role = pendingData.getRole() == null ? UserRole.CANDIDATE : pendingData.getRole();
        User user = User.builder()
                .username(pendingData.getUsername())
                .email(pendingData.getEmail())
                .password(pendingData.getPasswordHash())
                .roles(Set.of(role.getRoleName()))
                .status(UserStatus.PENDING)
                .company(role == UserRole.HR_MANAGER && pendingData.getCompany() != null
                        ? pendingData.getCompany().getName()
                        : null)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        return users.save(user);
    }

    private void validateHRManagerUniqueness(String companyName) {
        // Check if there's already an HR_MANAGER with the same company in our
        // auth-service database
        List<User> existingHRManagers = users.findByRolesContainingAndCompany("HR_MANAGER", companyName);
        if (!existingHRManagers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A HR Manager already exists for company: " + companyName);
        }

        log.info("HR_MANAGER uniqueness validation passed for company: {}", companyName);
        // TODO: In the future, also call job-service to validate company existence and
        // HR_MANAGER uniqueness
    }

    /**
     * Generate unique username from email.
     * Takes the part before @ and ensures uniqueness by adding a number suffix if
     * needed.
     * Example: "john.doe@example.com" -> "johndoe" or "johndoe1" if already
     * exists
     * Note: Only alphanumeric and underscores are allowed to match user-service
     * validation
     */
    private String generateUniqueUsername(String email) {
        // Extract part before @
        String baseUsername = email.split("@")[0].toLowerCase().trim();

        // Remove invalid characters (keep only alphanumeric and underscore)
        // This matches the validation in user-service UserEntity: ^[a-zA-Z0-9_]+$
        baseUsername = baseUsername.replaceAll("[^a-z0-9_]", "");

        // Ensure minimum length
        if (baseUsername.length() < 3) {
            baseUsername = baseUsername + "user";
        }

        // Check if username already exists in auth-service or user-service
        if (!usernameExistsAnywhere(baseUsername)) {
            return baseUsername;
        }

        // If exists, add number suffix
        int suffix = 1;
        String candidateUsername = baseUsername + suffix;
        while (usernameExistsAnywhere(candidateUsername)) {
            suffix++;
            candidateUsername = baseUsername + suffix;
            // Safety limit to prevent infinite loop
            if (suffix > 1000) {
                candidateUsername = baseUsername + "_" + UUID.randomUUID().toString().substring(0, 8);
                break;
            }
        }

        return candidateUsername;
    }

    /**
     * Check if username exists in either auth-service or user-service.
     */
    private boolean usernameExistsAnywhere(String username) {
        // Check auth-service first
        if (users.existsByUsername(username)) {
            return true;
        }

        // Check user-service
        try {
            Boolean existsInUserService = userServiceClient.existsByUsername(username);
            return Boolean.TRUE.equals(existsInUserService);
        } catch (Exception e) {
            log.warn("Could not validate username with user-service: {}. Assuming not exists.", e.getMessage());
            return false;
        }
    }

    private String normalizeDevice(String deviceId) {
        return (deviceId == null || deviceId.isBlank()) ? DEFAULT_DEVICE : deviceId.trim();
    }
}
