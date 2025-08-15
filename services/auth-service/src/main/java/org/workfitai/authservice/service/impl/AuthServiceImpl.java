package org.workfitai.authservice.service.impl;

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

    @Override
    public AuthResponse register(RegisterRequest req) {
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

        String access  = jwt.generateAccessToken(ud);
        String refresh = jwt.generateRefreshToken(ud);
        refreshStore.store(refresh, user.getId());

        return AuthResponse.of(access, refresh, jwt.getAccessExpMs());
    }

    @Override
    public AuthResponse login(LoginRequest req) {
        try {
            Authentication authentication = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            req.getUsernameOrEmail(),
                            req.getPassword()
                    )
            );
            UserDetails ud = (UserDetails) authentication.getPrincipal();
            String access  = jwt.generateAccessToken(ud);
            String refresh = jwt.generateRefreshToken(ud);

            // find the user's id
            String userId = users.findByUsername(ud.getUsername())
                    .orElseThrow()
                    .getId();
            refreshStore.store(refresh, userId);

            return AuthResponse.of(access, refresh, jwt.getAccessExpMs());

        } catch (BadCredentialsException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Invalid credentials");
        }
    }

    @Override
    public AuthResponse refresh(RefreshRequest req) {
        String oldToken = req.getRefreshToken();
        String userId   = refreshStore.getUserId(oldToken);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Invalid or expired refresh token");
        }

        // rotate
        refreshStore.delete(oldToken);

        User user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "User not found"));

        UserDetails ud = org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())
                .authorities(user.getRoles().toArray(new String[0]))
                .build();

        String access  = jwt.generateAccessToken(ud);
        String refresh = jwt.generateRefreshToken(ud);
        refreshStore.store(refresh, userId);

        return AuthResponse.of(access, refresh, jwt.getAccessExpMs());
    }
}
