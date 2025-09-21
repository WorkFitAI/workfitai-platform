package org.workfitai.authservice.service.impl;

import java.time.Instant;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.workfitai.authservice.constants.Messages;
import org.workfitai.authservice.dto.IssuedTokens;
import org.workfitai.authservice.dto.LoginRequest;
import org.workfitai.authservice.dto.RegisterRequest;
import org.workfitai.authservice.enums.UserRole;
import org.workfitai.authservice.model.User;
import org.workfitai.authservice.repository.UserRepository;
import org.workfitai.authservice.security.JwtService;
import org.workfitai.authservice.service.RefreshTokenService;
import org.workfitai.authservice.service.iAuthService;

import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements iAuthService {

    private final UserRepository users;
    private final BCryptPasswordEncoder encoder;
    private final AuthenticationManager authManager;
    private final JwtService jwt;
    private final RefreshTokenService refreshStore;

    private static final String DEFAULT_DEVICE = Messages.Misc.DEFAULT_DEVICE;

    @Override
    public IssuedTokens register(RegisterRequest req, String deviceId) {
        if (users.existsByUsername(req.getUsername()) ||
                users.existsByEmail(req.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    Messages.Error.USERNAME_EMAIL_ALREADY_IN_USE);
        }

        var user = User.builder()
                .username(req.getUsername())
                .email(req.getEmail())
                .password(encoder.encode(req.getPassword()))
                .roles(Set.of(UserRole.ADMIN.getRoleName()))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        users.save(user);

        UserDetails ud = org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())
                .authorities(UserRole.ADMIN.getRoleName())
                .build();

        String access = jwt.generateAccessToken(ud);
        String jti = jwt.newJti();
        String refresh = jwt.generateRefreshTokenWithJti(ud, jti);

        String dev = normalizeDevice(deviceId);
        refreshStore.saveJti(user.getId(), dev, jti); // device-bound jti

        return IssuedTokens.of(access, refresh, jwt.getAccessExpMs());
    }

    @Override
    public IssuedTokens login(LoginRequest req, String deviceId) {
        try {
            Authentication authentication = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            req.getUsernameOrEmail(), req.getPassword()));
            UserDetails ud = (UserDetails) authentication.getPrincipal();

            // Look up the user id by username
            String userId = users.findByUsername(ud.getUsername())
                    .orElseThrow()
                    .getId();

            String access = jwt.generateAccessToken(ud);
            String jti = jwt.newJti();
            String refresh = jwt.generateRefreshTokenWithJti(ud, jti);

            String dev = normalizeDevice(deviceId);
            refreshStore.saveJti(userId, dev, jti); // overwrite any previous jti for this device

            return IssuedTokens.of(access, refresh, jwt.getAccessExpMs());

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
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "TOKEN_INVALID");
        }

        String userId = users.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"))
                .getId();

        // Check device-scoped JTI in Redis
        String activeJti = refreshStore.getJti(userId, dev);
        if (activeJti == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED");
        if (!activeJti.equals(jti))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "TOKEN_INVALID");

        // Rotate
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

        refreshStore.saveJti(userId, dev, newJti); // overwrites + resets TTL

        return IssuedTokens.of(access, newRefresh, jwt.getAccessExpMs());
    }

    private String normalizeDevice(String deviceId) {
        return (deviceId == null || deviceId.isBlank()) ? DEFAULT_DEVICE : deviceId.trim();
    }
}
