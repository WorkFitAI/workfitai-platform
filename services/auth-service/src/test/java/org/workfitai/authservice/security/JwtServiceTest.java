package org.workfitai.authservice.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.workfitai.authservice.config.RsaKeyProperties;
import org.workfitai.authservice.enums.UserStatus;
import org.workfitai.authservice.repository.UserRepository;
import org.workfitai.authservice.service.iRoleService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @Mock iRoleService roleService;
    @Mock UserRepository userRepository;

    private JwtService jwtService;

    private static RSAPublicKey testPublicKey;
    private static RSAPrivateKey testPrivateKey;

    @BeforeAll
    static void generateKeyPair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();
        testPublicKey = (RSAPublicKey) pair.getPublic();
        testPrivateKey = (RSAPrivateKey) pair.getPrivate();
    }

    @BeforeEach
    void setUp() {
        RsaKeyProperties rsaKeys = new RsaKeyProperties(testPublicKey, testPrivateKey);
        jwtService = new JwtService(roleService, rsaKeys, userRepository);
        jwtService.setAccessExpMs(3_600_000L);   // 1 hour
        jwtService.setRefreshExpMs(604_800_000L); // 7 days
        jwtService.init();
    }

    private UserDetails candidateDetails() {
        return User.withUsername("candidate1")
                .password("hashed")
                .authorities(new SimpleGrantedAuthority("CANDIDATE"))
                .build();
    }

    private org.workfitai.authservice.model.User userEntity(String username, String... roles) {
        return org.workfitai.authservice.model.User.builder()
                .id("user-id-1")
                .username(username)
                .email(username + "@example.com")
                .roles(Set.of(roles))
                .status(UserStatus.ACTIVE)
                .build();
    }

    // ─── generateAccessToken ──────────────────────────────────────────────────

    @Test
    void generateAccessToken_returnsNonNullToken() {
        when(roleService.getPermissions("CANDIDATE")).thenReturn(Set.of("READ_PROFILE"));
        when(userRepository.findByUsername("candidate1")).thenReturn(
                Optional.of(userEntity("candidate1", "CANDIDATE")));

        String token = jwtService.generateAccessToken(candidateDetails());

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3); // header.payload.signature
    }

    @Test
    void generateAccessToken_tokenHasCorrectSubject() {
        when(roleService.getPermissions("CANDIDATE")).thenReturn(Set.of("READ_PROFILE"));
        when(userRepository.findByUsername("candidate1")).thenReturn(
                Optional.of(userEntity("candidate1", "CANDIDATE")));

        String token = jwtService.generateAccessToken(candidateDetails());
        Claims claims = jwtService.getClaims(token);

        assertThat(claims.getSubject()).isEqualTo("candidate1");
    }

    @Test
    void generateAccessToken_claimsContainRolesAndPermissions() {
        when(roleService.getPermissions("CANDIDATE")).thenReturn(Set.of("READ_PROFILE", "APPLY_JOB"));
        when(userRepository.findByUsername("candidate1")).thenReturn(
                Optional.of(userEntity("candidate1", "CANDIDATE")));

        String token = jwtService.generateAccessToken(candidateDetails());
        Claims claims = jwtService.getClaims(token);

        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) claims.get("roles");
        assertThat(roles).contains("CANDIDATE");

        @SuppressWarnings("unchecked")
        List<String> perms = (List<String>) claims.get("perms");
        assertThat(perms).contains("READ_PROFILE", "APPLY_JOB");
    }

    @Test
    void generateAccessToken_hrUser_includesCompanyIdClaim() {
        when(roleService.getPermissions("HR")).thenReturn(Set.of("MANAGE_APPLICATIONS"));
        org.workfitai.authservice.model.User hrUser = org.workfitai.authservice.model.User.builder()
                .id("hr-id")
                .username("hr1")
                .email("hr1@example.com")
                .roles(Set.of("HR"))
                .status(UserStatus.ACTIVE)
                .companyNo("TAX-001")
                .build();
        when(userRepository.findByUsername("hr1")).thenReturn(Optional.of(hrUser));

        UserDetails hrDetails = User.withUsername("hr1").password("x")
                .authorities(new SimpleGrantedAuthority("HR")).build();

        String token = jwtService.generateAccessToken(hrDetails);
        Claims claims = jwtService.getClaims(token);

        assertThat(claims.get("companyId")).isEqualTo("TAX-001");
    }

    @Test
    void generateAccessToken_candidateUser_doesNotIncludeCompanyIdClaim() {
        when(roleService.getPermissions("CANDIDATE")).thenReturn(Set.of("READ_PROFILE"));
        when(userRepository.findByUsername("candidate1")).thenReturn(
                Optional.of(userEntity("candidate1", "CANDIDATE")));

        String token = jwtService.generateAccessToken(candidateDetails());
        Claims claims = jwtService.getClaims(token);

        assertThat(claims.get("companyId")).isNull();
    }

    @Test
    void generateAccessToken_throwsWhenUserNotFound() {
        when(roleService.getPermissions(anyString())).thenReturn(Set.of());
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        UserDetails ghost = User.withUsername("ghost").password("x")
                .authorities(new SimpleGrantedAuthority("CANDIDATE")).build();

        assertThatThrownBy(() -> jwtService.generateAccessToken(ghost))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("User not found");
    }

    // ─── validateToken ────────────────────────────────────────────────────────

    @Test
    void validateToken_returnsTrueForValidToken() {
        when(roleService.getPermissions("CANDIDATE")).thenReturn(Set.of());
        when(userRepository.findByUsername("candidate1")).thenReturn(
                Optional.of(userEntity("candidate1", "CANDIDATE")));

        String token = jwtService.generateAccessToken(candidateDetails());

        assertThat(jwtService.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_returnsFalseForExpiredToken() {
        when(roleService.getPermissions("CANDIDATE")).thenReturn(Set.of());
        when(userRepository.findByUsername("candidate1")).thenReturn(
                Optional.of(userEntity("candidate1", "CANDIDATE")));

        // Use negative expiry to generate an already-expired token
        jwtService.setAccessExpMs(-1000L);
        String expiredToken = jwtService.generateAccessToken(candidateDetails());

        assertThat(jwtService.validateToken(expiredToken)).isFalse();
    }

    @Test
    void validateToken_returnsFalseForTamperedToken() {
        when(roleService.getPermissions("CANDIDATE")).thenReturn(Set.of());
        when(userRepository.findByUsername("candidate1")).thenReturn(
                Optional.of(userEntity("candidate1", "CANDIDATE")));

        String token = jwtService.generateAccessToken(candidateDetails());

        // Tamper the payload segment
        String[] parts = token.split("\\.");
        String tamperedPayload = parts[1].substring(0, parts[1].length() - 1) + "X";
        String tamperedToken = parts[0] + "." + tamperedPayload + "." + parts[2];

        assertThat(jwtService.validateToken(tamperedToken)).isFalse();
    }

    @Test
    void validateToken_returnsFalseForGarbage() {
        assertThat(jwtService.validateToken("not.a.jwt")).isFalse();
    }

    // ─── extractUsername ──────────────────────────────────────────────────────

    @Test
    void extractUsername_returnsSubjectFromToken() {
        when(roleService.getPermissions("CANDIDATE")).thenReturn(Set.of());
        when(userRepository.findByUsername("candidate1")).thenReturn(
                Optional.of(userEntity("candidate1", "CANDIDATE")));

        String token = jwtService.generateAccessToken(candidateDetails());

        assertThat(jwtService.extractUsername(token)).isEqualTo("candidate1");
    }

    @Test
    void extractUsername_throwsOnTamperedToken() {
        when(roleService.getPermissions("CANDIDATE")).thenReturn(Set.of());
        when(userRepository.findByUsername("candidate1")).thenReturn(
                Optional.of(userEntity("candidate1", "CANDIDATE")));

        String token = jwtService.generateAccessToken(candidateDetails());
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + ".invalidsig";

        assertThatThrownBy(() -> jwtService.extractUsername(tampered))
                .isInstanceOf(JwtException.class);
    }

    // ─── Refresh token ────────────────────────────────────────────────────────

    @Test
    void generateRefreshTokenWithJti_embeds_jti_extractable() {
        String jti = jwtService.newJti();
        String refreshToken = jwtService.generateRefreshTokenWithJti(candidateDetails(), jti);

        assertThat(refreshToken).isNotBlank();
        assertThat(jwtService.extractJti(refreshToken)).isEqualTo(jti);
    }

    @Test
    void generateRefreshTokenWithJti_subjectIsUsername() {
        String jti = jwtService.newJti();
        String refreshToken = jwtService.generateRefreshTokenWithJti(candidateDetails(), jti);

        assertThat(jwtService.extractUsername(refreshToken)).isEqualTo("candidate1");
    }

    @Test
    void newJti_producesUniqueValues() {
        String jti1 = jwtService.newJti();
        String jti2 = jwtService.newJti();

        assertThat(jti1).isNotEqualTo(jti2);
    }
}
