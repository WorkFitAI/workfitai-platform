package org.workfitai.authservice.service.impl;

import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.workfitai.authservice.dto.*;
import org.workfitai.authservice.model.User;
import org.workfitai.authservice.repository.UserRepository;
import org.workfitai.authservice.response.*;
import org.workfitai.authservice.security.JwtService;
import org.workfitai.authservice.service.*;

import java.time.Instant;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements iAuthService {

    private final UserRepository users;
    private final BCryptPasswordEncoder encoder;
    private final AuthenticationManager authManager;
    private final JwtService jwt;
    private final RefreshTokenService refreshStore;

    private static final String DEFAULT_DEVICE = "default";

    @Override
    public AuthResponse register(RegisterRequest req, String deviceId) {
        if (users.existsByUsername(req.getUsername()) ||
                users.existsByEmail(req.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Username or email already in use");
        }

        var user = User.builder()
                .username(req.getUsername())
                .email(req.getEmail())
                .password(encoder.encode(req.getPassword()))
                .roles(Set.of("USER"))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        users.save(user);

        UserDetails ud = org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())
                .authorities("USER")
                .build();

        String access = jwt.generateAccessToken(ud);
        String jti    = jwt.newJti();
        String refresh = jwt.generateRefreshTokenWithJti(ud, jti);

        String dev = normalizeDevice(deviceId);
        refreshStore.saveJti(user.getId(), dev, jti); // device-bound jti

        return AuthResponse.of(access, refresh, jwt.getAccessExpMs());
    }

    @Override
    public AuthResponse login(LoginRequest req, String deviceId) {
        try {
            Authentication authentication = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            req.getUsernameOrEmail(), req.getPassword())
            );
            UserDetails ud = (UserDetails) authentication.getPrincipal();

            // Look up the user id by username
            String userId = users.findByUsername(ud.getUsername())
                    .orElseThrow()
                    .getId();

            String access = jwt.generateAccessToken(ud);
            String jti    = jwt.newJti();
            String refresh = jwt.generateRefreshTokenWithJti(ud, jti);

            String dev = normalizeDevice(deviceId);
            refreshStore.saveJti(userId, dev, jti);     // overwrite any previous jti for this device

            return AuthResponse.of(access, refresh, jwt.getAccessExpMs());

        } catch (BadCredentialsException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
    }

    @Override
    public AuthResponse refresh(RefreshRequest req, String deviceId) {
        String dev = normalizeDevice(deviceId);
        String presented = req.getRefreshToken();

        // Parse claims — will throw on bad signature/expiry
        String username;
        String jti;
        try {
            username = jwt.extractUsername(presented);
            jti = jwt.extractJti(presented);
        } catch (JwtException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "TOKEN_INVALID");
        }

        // Resolve userId (we store by userId in Redis)
        String userId = users.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"))
                .getId();

        // Validate the jti for this user+device
        String activeJti = refreshStore.getJti(userId, dev);
        if (activeJti == null) {
            // No active session for this device (expired/evicted)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED");
        }
        if (!activeJti.equals(jti)) {
            // Presented refresh is not the currently active one (stolen/rotated)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "TOKEN_INVALID");
        }

        // Rotate: issue new tokens, overwrite stored jti (previous jti becomes invalid)
        User user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        UserDetails ud = org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())
                .authorities(user.getRoles().toArray(new String[0]))
                .build();

        String access = jwt.generateAccessToken(ud);
        String newJti = jwt.newJti();
        String newRefresh = jwt.generateRefreshTokenWithJti(ud, newJti);

        refreshStore.saveJti(userId, dev, newJti);  // overwrites + resets TTL

        return AuthResponse.of(access, newRefresh, jwt.getAccessExpMs());
    }

    private String normalizeDevice(String deviceId) {
        return (deviceId == null || deviceId.isBlank()) ? DEFAULT_DEVICE : deviceId.trim();
    }
}
