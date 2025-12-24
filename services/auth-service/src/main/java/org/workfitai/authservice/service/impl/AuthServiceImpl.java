package org.workfitai.authservice.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
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
import org.workfitai.authservice.document.TwoFactorAuth;
import org.workfitai.authservice.dto.kafka.NotificationEvent;
import org.workfitai.authservice.dto.kafka.UserRegistrationEvent;
import org.workfitai.authservice.dto.request.LoginRequest;
import org.workfitai.authservice.dto.request.PendingRegistration;
import org.workfitai.authservice.dto.request.RegisterRequest;
import org.workfitai.authservice.dto.request.Verify2FALoginRequest;
import org.workfitai.authservice.dto.request.VerifyOtpRequest;
import org.workfitai.authservice.dto.response.IssuedTokens;
import org.workfitai.authservice.dto.response.MeResponse;
import org.workfitai.authservice.dto.response.Partial2FALoginResponse;
import org.workfitai.authservice.enums.UserRole;
import org.workfitai.authservice.enums.UserStatus;
import org.workfitai.authservice.messaging.NotificationProducer;
import org.workfitai.authservice.messaging.UserRegistrationProducer;
import org.workfitai.authservice.model.User;
import org.workfitai.authservice.repository.TwoFactorAuthRepository;
import org.workfitai.authservice.repository.UserRepository;
import org.workfitai.authservice.security.JwtService;
import org.workfitai.authservice.service.OtpService;
import org.workfitai.authservice.service.RefreshTokenService;
import org.workfitai.authservice.service.SessionService;
import org.workfitai.authservice.service.TwoFactorAuthService;
import org.workfitai.authservice.service.iAuthService;
import org.workfitai.authservice.util.LogContext;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
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
    private final SessionService sessionService;
    private final TwoFactorAuthRepository twoFactorAuthRepository;
    private final TwoFactorAuthService twoFactorAuthService;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${app.frontend.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    private static final String TEMP_LOGIN_TOKEN_PREFIX = "temp:login:";
    private static final long TEMP_TOKEN_EXPIRY_MINUTES = 5;

    private static final String DEFAULT_DEVICE = Messages.Misc.DEFAULT_DEVICE;

    @Override
    public void register(RegisterRequest req) {
        UserRole role = req.getRole() == null ? UserRole.CANDIDATE : req.getRole();
        LogContext.setAction("REGISTER");
        LogContext.setEntityType("User");
        LogContext.setEntityId(req.getEmail());
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
        String companyNo = null;
        if (role == UserRole.HR && req.getHrProfile() != null) {
            String hrManagerEmail = req.getHrProfile().getHrManagerEmail();
            if (hrManagerEmail == null || hrManagerEmail.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "HR Manager email is required for HR registration. Please provide the email of your HR Manager.");
            }

            // Find HR Manager by email and get their companyNo
            User hrManager = users.findByEmailForCompanyNo(hrManagerEmail)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "HR Manager with email " + hrManagerEmail
                                    + " not found. Please verify the email address."));

            companyNo = hrManager.getCompanyNo();
            if (companyNo == null || companyNo.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "HR Manager account does not have a company number. Please contact your administrator.");
            }
        }
        if (role == UserRole.HR_MANAGER && req.getHrProfile() != null && req.getCompany() != null) {
            // Check if HR_MANAGER already exists for this company (by companyNo)
            validateHRManagerUniqueness(req.getCompany().getCompanyNo());
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

        // Check if phone number already exists in user-service (cross-service
        // validation)
        if (req.getPhoneNumber() != null && !req.getPhoneNumber().isBlank()) {
            try {
                Boolean phoneExistsInUserService = userServiceClient.existsByPhoneNumber(req.getPhoneNumber());
                if (Boolean.TRUE.equals(phoneExistsInUserService)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Phone number is already registered in the system");
                }
            } catch (ResponseStatusException e) {
                throw e; // Re-throw our validation errors
            } catch (Exception e) {
                log.warn("Could not validate phone number with user-service: {}. Proceeding with registration.",
                        e.getMessage());
                // Continue with registration - will be caught by Kafka consumer if duplicate
            }
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

        // Generate companyId UUID for HR_MANAGER
        String companyId = (role == UserRole.HR_MANAGER && req.getCompany() != null)
                ? UUID.randomUUID().toString()
                : null;

        // ✅ IMPORTANT: Update PendingRegistration with companyId after generation
        // This ensures companyId is available when building CompanyData during OTP
        // verification
        if (companyId != null) {
            pendingData.setCompanyId(companyId);
            otpService.savePendingRegistration(req.getEmail(), pendingData); // Re-save with companyId
        }

        // Determine companyNo based on role
        String finalCompanyNo = null;
        if (role == UserRole.HR_MANAGER && req.getCompany() != null) {
            finalCompanyNo = req.getCompany().getCompanyNo();
        } else if (role == UserRole.HR) {
            finalCompanyNo = companyNo; // Already looked up from HR Manager
        }

        var user = User.builder()
                .username(username)
                .email(req.getEmail())
                .password(encodedPassword)
                .roles(Set.of(role.getRoleName()))
                .status(UserStatus.PENDING)
                .companyId(companyId)
                .companyNo((role == UserRole.HR_MANAGER || role == UserRole.HR) && req.getCompany() != null
                        ? req.getCompany().getCompanyNo()
                        : null)
                .companyNo(finalCompanyNo)
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

        // ✅ FIX: Update companyId/companyNo if user provides company info (HR_MANAGER
        // case)
        // This handles scenario where user registers without complete data, then
        // resends with company
        if (role == UserRole.HR_MANAGER && req.getCompany() != null) {
            // Only generate new companyId if user doesn't have one yet
            if (existingUser.getCompanyId() == null) {
                existingUser.setCompanyId(UUID.randomUUID().toString());
                log.info("Generated companyId {} for existing pending user {}", existingUser.getCompanyId(),
                        req.getEmail());
            }
            existingUser.setCompanyNo(req.getCompany().getCompanyNo());
        } else if (role == UserRole.HR && req.getHrProfile() != null) {
            // For HR role, look up HR Manager's companyNo
            String hrManagerEmail = req.getHrProfile().getHrManagerEmail();
            if (hrManagerEmail != null && !hrManagerEmail.isBlank()) {
                User hrManager = users.findByEmailForCompanyNo(hrManagerEmail)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "HR Manager with email " + hrManagerEmail
                                        + " not found. Please verify the email address."));

                String hrCompanyNo = hrManager.getCompanyNo();
                if (hrCompanyNo != null && !hrCompanyNo.isBlank()) {
                    existingUser.setCompanyNo(hrCompanyNo);
                }
            }
        }

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
                .companyId(existingUser.getCompanyId()) // ✅ Set companyId from User
                .build();
        otpService.savePendingRegistration(req.getEmail(), pendingData);

        // Send OTP email
        sendOtpEmail(req.getEmail(), otp, req.getFullName());

        log.info("OTP resent for pending user: {}", req.getEmail());
    }

    @Override
    public Object login(LoginRequest req, String deviceId, HttpServletRequest request) {
        try {
            Authentication authentication = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            req.getUsernameOrEmail(), req.getPassword()));
            UserDetails ud = (UserDetails) authentication.getPrincipal();

            // Look up the user by username and check status
            User user = users.findByUsername(ud.getUsername())
                    .orElseThrow(
                            () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, Messages.Error.USER_NOT_FOUND));

            // Check if user is blocked by admin
            if (Boolean.TRUE.equals(user.getIsBlocked())) {
                log.warn("Blocked user attempted to login: {}", user.getUsername());
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Your account has been blocked by administrator. Please contact support.");
            }

            // Check if user is active
            if (user.getStatus() == UserStatus.INACTIVE) {
                // Check with user-service if account can be reactivated (within 30 days)
                try {
                    Boolean canReactivate = userServiceClient.checkAndReactivateAccount(user.getUsername());
                    if (Boolean.TRUE.equals(canReactivate)) {
                        // Account reactivated, update status to ACTIVE
                        user.setStatus(UserStatus.ACTIVE);
                        user.setUpdatedAt(Instant.now());
                        users.save(user);
                        log.info("Account auto-reactivated for user: {}", user.getUsername());
                    } else {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                "Your account has been deactivated for more than 30 days and cannot be restored. Please create a new account.");
                    }
                } catch (ResponseStatusException e) {
                    throw e;
                } catch (Exception e) {
                    log.error("Failed to check reactivation status: {}", e.getMessage());
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your account has been deactivated.");
                }
            } else if (user.getStatus() != UserStatus.ACTIVE) {
                String message = switch (user.getStatus()) {
                    case PENDING -> "Please verify your email before logging in.";
                    case WAIT_APPROVED -> "Your account is pending approval. Please wait for administrator approval.";
                    default -> "Your account is not active.";
                };
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
            }

            // ✅ NEW: Check if 2FA is enabled
            Optional<TwoFactorAuth> twoFactorAuth = twoFactorAuthRepository.findByUserId(user.getId());
            if (twoFactorAuth.isPresent() && Boolean.TRUE.equals(twoFactorAuth.get().getEnabled())) {
                LogContext.setAction("LOGIN_2FA_REQUIRED");
                LogContext.setEntityType("User");
                LogContext.setEntityId(user.getId());
                log.info("2FA enabled for user: {}. Initiating 2FA flow", user.getUsername());
                return initiate2FALogin(user, twoFactorAuth.get(), deviceId);
            }

            // Extract geolocation from request if available
            Double latitude = null;
            Double longitude = null;
            if (req.getGeolocation() != null) {
                latitude = req.getGeolocation().getLatitude();
                longitude = req.getGeolocation().getLongitude();
                log.info("Browser geolocation provided: lat={}, lon={}", latitude, longitude);
            }

            // ✅ No 2FA - proceed with normal login
            LogContext.setAction("LOGIN");
            LogContext.setEntityType("User");
            LogContext.setEntityId(user.getId());
            return completeLogin(user, ud, deviceId, request, latitude, longitude);

        } catch (BadCredentialsException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, Messages.Error.INVALID_CREDENTIALS);
        }
    }

    /**
     * Initiate 2FA login flow - generate temp token and send code
     */
    private Partial2FALoginResponse initiate2FALogin(User user, TwoFactorAuth twoFactorAuth, String deviceId) {
        // Generate temporary token (valid for 5 minutes)
        String tempToken = UUID.randomUUID().toString();
        String redisKey = TEMP_LOGIN_TOKEN_PREFIX + tempToken;

        // Store user info in Redis
        String userData = String.format("%s:%s:%s", user.getId(), user.getUsername(), deviceId);
        redisTemplate.opsForValue().set(redisKey, userData, TEMP_TOKEN_EXPIRY_MINUTES, TimeUnit.MINUTES);

        String method = twoFactorAuth.getMethod();

        if ("EMAIL".equals(method)) {
            // Generate and send 6-digit code via email
            String code = twoFactorAuthService.generateEmailCode(user.getId());
            sendEmail2FACode(user, code);

            return Partial2FALoginResponse.builder()
                    .tempToken(tempToken)
                    .message("2FA code sent to your email")
                    .method("EMAIL")
                    .maskedEmail(maskEmail(user.getEmail()))
                    .expiresIn(TEMP_TOKEN_EXPIRY_MINUTES * 60) // seconds
                    .require2FA(true)
                    .build();

        } else if ("TOTP".equals(method)) {
            // User needs to enter TOTP code from authenticator app
            return Partial2FALoginResponse.builder()
                    .tempToken(tempToken)
                    .message("Enter the 6-digit code from your authenticator app")
                    .method("TOTP")
                    .expiresIn(TEMP_TOKEN_EXPIRY_MINUTES * 60) // seconds
                    .require2FA(true)
                    .build();
        }

        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid 2FA method");
    }

    /**
     * Complete login after 2FA verification
     */
    public IssuedTokens verify2FALogin(Verify2FALoginRequest request, HttpServletRequest httpRequest) {
        // Validate temp token
        String redisKey = TEMP_LOGIN_TOKEN_PREFIX + request.getTempToken();
        String userData = redisTemplate.opsForValue().get(redisKey);

        if (userData == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired temporary token");
        }

        // Parse user data: userId:username:deviceId
        String[] parts = userData.split(":");
        if (parts.length != 3) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid token data");
        }

        String userId = parts[0];
        String username = parts[1];
        String deviceId = parts[2];

        // Get user and 2FA settings
        User user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        TwoFactorAuth twoFactorAuth = twoFactorAuthRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "2FA configuration not found"));

        // Verify 2FA code or backup code
        boolean isValid;
        if (Boolean.TRUE.equals(request.getUseBackupCode())) {
            // Verify backup code
            isValid = twoFactorAuthService.verifyBackupCode(twoFactorAuth, request.getCode());
            if (!isValid) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid backup code");
            }
            log.info("User {} verified with backup code", username);
        } else {
            // Verify regular 2FA code
            isValid = twoFactorAuthService.verify2FACode(userId, request.getCode(), twoFactorAuth.getMethod());
            if (!isValid) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid 2FA code");
            }
        }

        // Delete temp token (one-time use)
        redisTemplate.delete(redisKey);

        LogContext.setAction("LOGIN_2FA_VERIFIED");
        LogContext.setEntityType("User");
        LogContext.setEntityId(user.getId());

        // Complete login
        UserDetails ud = org.springframework.security.core.userdetails.User
                .withUsername(username)
                .password(user.getPassword())
                .authorities(user.getRoles().toArray(new String[0]))
                .build();

        // 2FA verification happens after initial login, no new geolocation needed
        return completeLogin(user, ud, deviceId, httpRequest, null, null);
    }

    /**
     * Complete normal login (no 2FA) - generate tokens and create session
     */
    private IssuedTokens completeLogin(User user, UserDetails ud, String deviceId, HttpServletRequest request,
            Double latitude, Double longitude) {
        String access = jwt.generateAccessToken(ud);
        String jti = jwt.newJti();
        String refresh = jwt.generateRefreshTokenWithJti(ud, jti);

        String dev = normalizeDevice(deviceId);
        refreshStore.saveJti(user.getId(), dev, jti); // Redis: Store refresh token JTI

        // MongoDB: Create session with request context for device/IP detection and
        // optional browser geolocation
        sessionService.createSession(user.getId(), jti, jwt.getRefreshExpMs(), request, latitude, longitude);

        Set<String> roles = user.getRoles() != null ? user.getRoles() : Set.of();
        return IssuedTokens.of(access, refresh, jwt.getAccessExpMs(), user.getUsername(), roles, user.getCompanyNo());
    }

    private void sendEmail2FACode(User user, String code) {
        Map<String, Object> data = Map.of(
                "fullName", user.getFullName() != null ? user.getFullName() : user.getUsername(),
                "code", code,
                "validUntil", "5 minutes");

        NotificationEvent event = NotificationEvent.builder()
                .recipientEmail(user.getEmail())
                .templateType("2fa-login-code")
                .sendEmail(true)
                .createInAppNotification(false)
                .metadata(data)
                .build();

        notificationProducer.send(event);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        String[] parts = email.split("@");
        String local = parts[0];
        String domain = parts[1];

        if (local.length() <= 2) {
            return "**@" + domain;
        }

        return local.substring(0, 2) + "****@" + domain;
    }

    @Override
    public void logout(String deviceId, String username) {
        String userId = users.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, Messages.Error.USER_NOT_FOUND))
                .getId();
        LogContext.setAction("LOGOUT");
        LogContext.setEntityType("User");
        LogContext.setEntityId(userId);
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

        Set<String> roles = user.getRoles() != null ? user.getRoles() : Set.of();
        refreshStore.saveJti(userId, dev, newJti); // overwrites + resets TTL

        return IssuedTokens.of(access, newRefresh, jwt.getAccessExpMs(), user.getUsername(), roles,
                user.getCompanyNo());
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

        LogContext.setAction("VERIFY_OTP");
        LogContext.setEntityType("User");
        LogContext.setEntityId(user.getId());

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
                        .company(buildCompanyData(user, pendingData))
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

    private UserRegistrationEvent.CompanyData buildCompanyData(User user, PendingRegistration pendingData) {
        if (pendingData.getCompany() == null) {
            return null;
        }

        // ✅ FIX: Use companyId from PendingRegistration (preferred) or User entity
        // (fallback)
        // PendingRegistration should have companyId set during registration
        String companyId = pendingData.getCompanyId();
        if (companyId == null) {
            companyId = user.getCompanyId(); // Fallback to User if not in PendingRegistration
            log.warn("companyId not found in PendingRegistration, using from User: {}", companyId);
        }

        return UserRegistrationEvent.CompanyData.builder()
                .companyId(companyId) // Use from PendingRegistration or User
                .companyNo(pendingData.getCompany().getCompanyNo())
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
                        "loginUrl", frontendBaseUrl + "/login"))
                .sendEmail(true)
                .createInAppNotification(false) // Transactional email only
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
                        "loginUrl", frontendBaseUrl + "/login",
                        "isCandidate", "true"))
                .sendEmail(true)
                .createInAppNotification(true) // Important account event - send both email and in-app
                .notificationType("ACCOUNT_ACTIVATED")
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
                        "loginUrl", frontendBaseUrl + "/login"))
                .sendEmail(true)
                .createInAppNotification(true) // Important status update - send both
                .notificationType("ACCOUNT_PENDING")
                .build());
    }

    private User createUserFromPending(PendingRegistration pendingData) {
        UserRole role = pendingData.getRole() == null ? UserRole.CANDIDATE : pendingData.getRole();

        // ✅ FIX: Check if User already exists (resend OTP case) and reuse companyId
        // DO NOT generate new UUID if user already has one in MongoDB
        String companyId = null;
        if (role == UserRole.HR_MANAGER && pendingData.getCompany() != null) {
            // Try to find existing user to reuse companyId
            User existingUser = users.findByEmail(pendingData.getEmail()).orElse(null);
            if (existingUser != null && existingUser.getCompanyId() != null) {
                companyId = existingUser.getCompanyId(); // Reuse existing
                log.info("Reusing existing companyId {} for user {}", companyId, pendingData.getEmail());
            } else {
                companyId = UUID.randomUUID().toString(); // Generate new only if not exists
                log.info("Generated new companyId {} for user {}", companyId, pendingData.getEmail());
            }
        }

        User user = User.builder()
                .username(pendingData.getUsername())
                .email(pendingData.getEmail())
                .password(pendingData.getPasswordHash())
                .roles(Set.of(role.getRoleName()))
                .status(UserStatus.PENDING)
                .companyId(companyId)
                .companyNo(role == UserRole.HR_MANAGER && pendingData.getCompany() != null
                        ? pendingData.getCompany().getCompanyNo()
                        : null)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        return users.save(user);
    }

    private void validateHRManagerUniqueness(String companyNo) {
        // Check if there's already an HR_MANAGER with the same companyNo in our
        // auth-service database
        List<User> existingHRManagers = users.findByRolesContainingAndCompanyNo("HR_MANAGER", companyNo);
        if (!existingHRManagers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A HR Manager already exists for company with tax ID: " + companyNo);
        }

        log.info("HR_MANAGER uniqueness validation passed for companyNo: {}", companyNo);
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

    @Override
    public MeResponse getCurrentUser(String username) {
        if (username == null || username.isBlank()) {
            return MeResponse.unauthenticated();
        }

        return users.findByUsername(username)
                .map(user -> {
                    Set<String> roles = user.getRoles() != null ? user.getRoles() : Set.of();
                    return MeResponse.authenticated(user.getUsername(), roles, user.getCompanyNo());
                })
                .orElse(MeResponse.unauthenticated());
    }
}
