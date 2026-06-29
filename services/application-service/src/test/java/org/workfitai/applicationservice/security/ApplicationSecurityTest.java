package org.workfitai.applicationservice.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.workfitai.applicationservice.exception.ForbiddenException;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.repository.ApplicationRepository;

@ExtendWith(MockitoExtension.class)
class ApplicationSecurityTest {

    @Mock ApplicationRepository applicationRepository;
    @InjectMocks ApplicationSecurity applicationSecurity;

    private JwtAuthenticationToken candidateAuth;
    private JwtAuthenticationToken adminAuth;
    private JwtAuthenticationToken hrAuth;
    private JwtAuthenticationToken hrWithCompanyAuth;
    private Application application;

    @BeforeEach
    void setUp() {
        candidateAuth = jwtToken("candidate1", Map.of(), List.of("ROLE_CANDIDATE", "application:read", "application:list", "application:create"));
        adminAuth = jwtToken("admin1", Map.of(), List.of("ROLE_ADMIN", "application:review", "application:update"));
        hrAuth = jwtToken("hr1", Map.of(), List.of("ROLE_HR", "application:review"));
        hrWithCompanyAuth = jwtToken("hr1", Map.of("companyId", "company-1"), List.of("ROLE_HR", "application:review", "application:assign"));

        application = Application.builder()
                .id("app-1").username("candidate1").companyId("company-1")
                .status(ApplicationStatus.APPLIED)
                .notes(new ArrayList<>()).statusHistory(new ArrayList<>())
                .build();
    }

    // ─── getCurrentUsername ───────────────────────────────────────────────────

    @Test
    void getCurrentUsername_jwtAuth_returnsSubject() {
        assertThat(applicationSecurity.getCurrentUsername(candidateAuth)).isEqualTo("candidate1");
    }

    @Test
    void getCurrentUsername_nonJwtAuth_throwsIllegalState() {
        var nonJwt = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "user", null, List.of());
        assertThatThrownBy(() -> applicationSecurity.getCurrentUsername(nonJwt))
                .isInstanceOf(IllegalStateException.class);
    }

    // ─── getCurrentCompanyId ──────────────────────────────────────────────────

    @Test
    void getCurrentCompanyId_claimPresent_returnsCompanyId() {
        assertThat(applicationSecurity.getCurrentCompanyId(hrWithCompanyAuth)).isEqualTo("company-1");
    }

    @Test
    void getCurrentCompanyId_noClaim_returnsNull() {
        assertThat(applicationSecurity.getCurrentCompanyId(candidateAuth)).isNull();
    }

    // ─── isOwner ─────────────────────────────────────────────────────────────

    @Test
    void isOwner_ownerExists_returnsTrue() {
        when(applicationRepository.existsByIdAndUsernameAndDeletedAtIsNull("app-1", "candidate1"))
                .thenReturn(true);
        assertThat(applicationSecurity.isOwner("app-1", candidateAuth)).isTrue();
    }

    @Test
    void isOwner_notOwner_returnsFalse() {
        when(applicationRepository.existsByIdAndUsernameAndDeletedAtIsNull("app-1", "candidate1"))
                .thenReturn(false);
        assertThat(applicationSecurity.isOwner("app-1", candidateAuth)).isFalse();
    }

    // ─── canView ──────────────────────────────────────────────────────────────

    @Test
    void canView_adminHasReviewPermission_returnsTrue() {
        assertThat(applicationSecurity.canView("app-1", adminAuth)).isTrue();
    }

    @Test
    void canView_candidateIsOwner_returnsTrue() {
        when(applicationRepository.existsByIdAndUsernameAndDeletedAtIsNull("app-1", "candidate1"))
                .thenReturn(true);
        assertThat(applicationSecurity.canView("app-1", candidateAuth)).isTrue();
    }

    @Test
    void canView_candidateIsNotOwner_returnsFalse() {
        when(applicationRepository.existsByIdAndUsernameAndDeletedAtIsNull("app-1", "candidate1"))
                .thenReturn(false);
        assertThat(applicationSecurity.canView("app-1", candidateAuth)).isFalse();
    }

    // ─── isAdmin / isHR / isCandidate ─────────────────────────────────────────

    @Test
    void isAdmin_adminAuth_returnsTrue() {
        assertThat(applicationSecurity.isAdmin(adminAuth)).isTrue();
    }

    @Test
    void isAdmin_candidateAuth_returnsFalse() {
        assertThat(applicationSecurity.isAdmin(candidateAuth)).isFalse();
    }

    @Test
    void isHR_hrAuth_returnsTrue() {
        assertThat(applicationSecurity.isHR(hrAuth)).isTrue();
    }

    @Test
    void isCandidate_candidateAuth_returnsTrue() {
        assertThat(applicationSecurity.isCandidate(candidateAuth)).isTrue();
    }

    // ─── isSameCompany ────────────────────────────────────────────────────────

    @Test
    void isSameCompany_adminAlwaysTrue() {
        assertThat(applicationSecurity.isSameCompany("any-company", adminAuth)).isTrue();
    }

    @Test
    void isSameCompany_hrMatchingCompany_returnsTrue() {
        assertThat(applicationSecurity.isSameCompany("company-1", hrWithCompanyAuth)).isTrue();
    }

    @Test
    void isSameCompany_hrDifferentCompany_returnsFalse() {
        assertThat(applicationSecurity.isSameCompany("other-company", hrWithCompanyAuth)).isFalse();
    }

    @Test
    void isSameCompany_noCompanyIdInToken_returnsFalse() {
        assertThat(applicationSecurity.isSameCompany("company-1", hrAuth)).isFalse();
    }

    // ─── requireOwnership ─────────────────────────────────────────────────────

    @Test
    void requireOwnership_owner_passes() {
        when(applicationRepository.findByIdAndDeletedAtIsNull("app-1"))
                .thenReturn(Optional.of(application));

        applicationSecurity.requireOwnership("app-1", candidateAuth); // must not throw
    }

    @Test
    void requireOwnership_notOwner_throwsForbidden() {
        Application otherApp = Application.builder().id("app-1").username("other_user").build();
        when(applicationRepository.findByIdAndDeletedAtIsNull("app-1"))
                .thenReturn(Optional.of(otherApp));

        assertThatThrownBy(() -> applicationSecurity.requireOwnership("app-1", candidateAuth))
                .isInstanceOf(ForbiddenException.class);
    }

    // ─── isNoteAuthor ─────────────────────────────────────────────────────────

    @Test
    void isNoteAuthor_matchingAuthor_returnsTrue() {
        Application.Note note = Application.Note.builder()
                .id("note-1").author("hr1").content("test")
                .candidateVisible(false).createdAt(java.time.Instant.now()).build();
        application.setNotes(new ArrayList<>(List.of(note)));

        when(applicationRepository.findByIdAndDeletedAtIsNull("app-1"))
                .thenReturn(Optional.of(application));

        assertThat(applicationSecurity.isNoteAuthor("app-1", "note-1", hrAuth)).isTrue();
    }

    @Test
    void isNoteAuthor_differentAuthor_returnsFalse() {
        Application.Note note = Application.Note.builder()
                .id("note-1").author("other_hr").content("test")
                .candidateVisible(false).createdAt(java.time.Instant.now()).build();
        application.setNotes(new ArrayList<>(List.of(note)));

        when(applicationRepository.findByIdAndDeletedAtIsNull("app-1"))
                .thenReturn(Optional.of(application));

        assertThat(applicationSecurity.isNoteAuthor("app-1", "note-1", hrAuth)).isFalse();
    }

    // ─── helper ───────────────────────────────────────────────────────────────

    private JwtAuthenticationToken jwtToken(String subject, Map<String, Object> extraClaims, List<String> authorities) {
        Map<String, Object> claims = new java.util.HashMap<>(extraClaims);
        claims.put("sub", subject);

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claims(c -> c.putAll(claims))
                .build();

        List<SimpleGrantedAuthority> grantedAuthorities = authorities.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();

        return new JwtAuthenticationToken(jwt, grantedAuthorities, subject);
    }
}
