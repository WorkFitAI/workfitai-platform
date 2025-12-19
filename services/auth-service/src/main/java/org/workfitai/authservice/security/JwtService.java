package org.workfitai.authservice.security;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.workfitai.authservice.config.RsaKeyProperties;
import org.workfitai.authservice.constants.Messages;
import org.workfitai.authservice.model.User;
import org.workfitai.authservice.repository.UserRepository;
import org.workfitai.authservice.service.iRoleService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * Generates and validates JWTs using RS256 algorithm with RSA public/private
 * key pair.
 * This provides better security than HS256 as it uses asymmetric cryptography.
 */
@Data
@Component
@RequiredArgsConstructor
public class JwtService {

    private final iRoleService roleService;
    private final RsaKeyProperties rsaKeys;
    private final UserRepository userRepository;

    @Value("${auth.jwt.access-exp-ms}")
    private long accessExpMs;

    @Value("${auth.jwt.refresh-exp-ms}")
    private long refreshExpMs;

    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;

    @PostConstruct
    public void init() {
        this.privateKey = rsaKeys.privateKey();
        this.publicKey = rsaKeys.publicKey();

        if (this.privateKey == null) {
            throw new IllegalStateException("RSA private key must be configured for JWT signing");
        }
        if (this.publicKey == null) {
            throw new IllegalStateException("RSA public key must be configured for JWT verification");
        }
    }

    /**
     * Issues an access token containing:
     * - subject = username
     * - claim "email" = user email
     * - claim "roles" = list of role names
     * - claim "perms" = union of all permissions for those roles
     * - claim "companyId" = company UUID (only for HR/HR_MANAGER)
     */
    public String generateAccessToken(UserDetails user) {
        List<String> roles = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        // fetch all permissions for these roles
        Set<String> perms = roles.stream()
                .flatMap(r -> roleService.getPermissions(r).stream())
                .collect(Collectors.toSet());

        // Get user entity to access email
        User userEntity = userRepository.findByUsername(user.getUsername())
                .orElseThrow(() -> new IllegalStateException("User not found: " + user.getUsername()));

        var builder = Jwts.builder()
                .subject(user.getUsername())
                .issuer(Messages.JWT.ISSUER)
                .claim(Messages.JWT.ROLES_CLAIM, roles)
                .claim(Messages.JWT.PERMISSIONS_CLAIM, perms)
                .claim("email", userEntity.getEmail()); // ✅ Add email claim

        // Add companyId claim for HR/HR_MANAGER users
        if (roles.contains("HR") || roles.contains("HR_MANAGER")) {
            if (userEntity.getCompanyNo() != null) {
                builder.claim("companyId", userEntity.getCompanyNo());
            }
        }

        return builder
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessExpMs))
                .signWith(privateKey) // RS256 is automatically selected for RSA keys
                .compact();
    }

    /** Validate signature & expiry */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token);
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
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** Create a new random JTI */
    public String newJti() {
        return UUID.randomUUID().toString();
    }

    /** Issue a refresh token carrying a specific jti */
    public String generateRefreshTokenWithJti(UserDetails user, String jti) {
        return Jwts.builder()
                .subject(user.getUsername())
                .issuer(Messages.JWT.ISSUER)
                .id(jti) // <-- make jti part of the token
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpMs))
                .signWith(privateKey) // RS256 is automatically selected for RSA keys
                .compact();
    }

    /** Extract the jti from a refresh token */
    public String extractJti(String token) {
        return getClaims(token).getId();
    }
}