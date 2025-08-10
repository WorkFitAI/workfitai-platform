package org.workfitai.authservice.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.workfitai.authservice.service.iRoleService;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates and validates JWTs, now embedding both roles and permissions.
 */
@Data
@Component
@RequiredArgsConstructor
public class JwtService {

    private final iRoleService roleService;

    @Value("${auth.jwt.secret}")
    private String secret;

    @Value("${auth.jwt.access-exp-ms}")
    private long accessExpMs;

    @Value("${auth.jwt.refresh-exp-ms}")
    private long refreshExpMs;

    private Key key;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Issues an access token containing:
     *  - subject = username
     *  - claim "roles" = list of role names
     *  - claim "perms" = union of all permissions for those roles
     */
    public String generateAccessToken(UserDetails user) {
        List<String> roles = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        // fetch all permissions for these roles
        Set<String> perms = roles.stream()
                .flatMap(r -> roleService.getPermissions(r).stream())
                .collect(Collectors.toSet());

        return Jwts.builder()
                .setSubject(user.getUsername())
                .claim("roles", roles)
                .claim("perms", perms)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + accessExpMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /** As before, no change needed: separate refresh-token logic */
    public String generateRefreshToken(UserDetails user) {
        return Jwts.builder()
                .setSubject(user.getUsername())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + refreshExpMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /** Validate signature & expiry */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (JwtException ex) {
            return false;
        }
    }

    /** Convenience: extract username from any valid token */
    public String extractUsername(String token) {
        return getClaims(token).getSubject();
    }

    /** Expose raw claims so the filter can read roles+perms */
    public Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}