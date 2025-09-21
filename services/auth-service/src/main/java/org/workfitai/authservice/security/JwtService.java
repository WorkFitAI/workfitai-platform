package org.workfitai.authservice.security;

import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.workfitai.authservice.constants.Messages;
import org.workfitai.authservice.service.iRoleService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;

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
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(Messages.Error.INVALID_SECRET_PROVIDED);
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Issues an access token containing:
     * - subject = username
     * - claim "roles" = list of role names
     * - claim "perms" = union of all permissions for those roles
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
                .setIssuer(Messages.JWT.ISSUER)
                .claim(Messages.JWT.ROLES_CLAIM, roles)
                .claim(Messages.JWT.PERMISSIONS_CLAIM, perms)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + accessExpMs))
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

    /** Create a new random JTI */
    public String newJti() {
        return UUID.randomUUID().toString();
    }

    /** Issue a refresh token carrying a specific jti */
    public String generateRefreshTokenWithJti(UserDetails user, String jti) {
        return Jwts.builder()
                .setSubject(user.getUsername())
                .setIssuer(Messages.JWT.ISSUER)
                .setId(jti) // <-- make jti part of the token
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + refreshExpMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /** Extract the jti from a refresh token */
    public String extractJti(String token) {
        return getClaims(token).getId();
    }
}