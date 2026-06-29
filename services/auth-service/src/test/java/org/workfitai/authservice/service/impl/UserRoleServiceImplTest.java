package org.workfitai.authservice.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.workfitai.authservice.model.Role;
import org.workfitai.authservice.model.User;
import org.workfitai.authservice.repository.RoleRepository;
import org.workfitai.authservice.repository.UserRepository;
import org.workfitai.authservice.service.iGrantAuditService;

@ExtendWith(MockitoExtension.class)
class UserRoleServiceImplTest {

    @Mock UserRepository users;
    @Mock RoleRepository roles;
    @Mock iGrantAuditService auditService;
    @InjectMocks UserRoleServiceImpl userRoleService;

    private User adminUser;
    private User targetUser;

    @BeforeEach
    void setUpUsers() {
        adminUser = new User();
        adminUser.setId("admin-id");
        adminUser.setUsername("admin");
        adminUser.setRoles(new HashSet<>(Set.of("ADMIN")));

        targetUser = new User();
        targetUser.setId("bob-id");
        targetUser.setUsername("bob");
        targetUser.setRoles(new HashSet<>(Set.of("CANDIDATE")));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void setAdminContext() {
        var auth = new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void setHrManagerContext(String hrManagerUsername) {
        var auth = new UsernamePasswordAuthenticationToken(
                hrManagerUsername, null, List.of(new SimpleGrantedAuthority("HR_MANAGER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ─── grantRoleToUser ──────────────────────────────────────────────────────

    @Test
    void grantRoleToUser_adminGrantsHrToOther_succeeds() {
        // Arrange
        setAdminContext();
        Role hrRole = new Role();
        hrRole.setName("HR");
        when(users.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(users.findByUsername("bob")).thenReturn(Optional.of(targetUser));
        when(roles.findByName("HR")).thenReturn(Optional.of(hrRole));
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        userRoleService.grantRoleToUser("bob", "HR");

        // Assert
        verify(users).save(targetUser);
        verify(auditService).logGrant("admin", "bob", "ROLE", "HR");
    }

    @Test
    void grantRoleToUser_selfGrant_throwsAccessDenied() {
        // Arrange
        setAdminContext();

        // Act & Assert — admin cannot grant a role to themselves
        assertThatThrownBy(() -> userRoleService.grantRoleToUser("admin", "HR"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void grantRoleToUser_roleNotFound_throwsNoSuchElement() {
        // Arrange
        setAdminContext();
        when(users.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(users.findByUsername("bob")).thenReturn(Optional.of(targetUser));
        when(roles.findByName("UNKNOWN")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userRoleService.grantRoleToUser("bob", "UNKNOWN"))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void grantRoleToUser_roleAlreadyGranted_throwsIllegalArgument() {
        // Arrange
        setAdminContext();
        targetUser.getRoles().add("HR");   // already has HR
        Role hrRole = new Role();
        hrRole.setName("HR");
        when(users.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(users.findByUsername("bob")).thenReturn(Optional.of(targetUser));
        when(roles.findByName("HR")).thenReturn(Optional.of(hrRole));

        // Act & Assert
        assertThatThrownBy(() -> userRoleService.grantRoleToUser("bob", "HR"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── revokeRoleFromUser ───────────────────────────────────────────────────

    @Test
    void revokeRoleFromUser_adminRevokesRole_succeeds() {
        // Arrange
        setAdminContext();
        targetUser.getRoles().add("HR");  // has CANDIDATE + HR; remove HR, retains CANDIDATE
        Role hrRole = new Role();
        hrRole.setName("HR");
        when(users.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(users.findByUsername("bob")).thenReturn(Optional.of(targetUser));
        when(roles.findByName("HR")).thenReturn(Optional.of(hrRole));
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        userRoleService.revokeRoleFromUser("bob", "HR");

        // Assert
        verify(users).save(targetUser);
        verify(auditService).logRevoke("admin", "bob", "ROLE", "HR");
    }

    @Test
    void revokeRoleFromUser_removingLastBuiltInRole_throwsIllegalArgument() {
        // Arrange
        setAdminContext();
        // bob only has CANDIDATE — removing it leaves him with no built-in role
        Role candidateRole = new Role();
        candidateRole.setName("CANDIDATE");
        when(users.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(users.findByUsername("bob")).thenReturn(Optional.of(targetUser));
        when(roles.findByName("CANDIDATE")).thenReturn(Optional.of(candidateRole));

        // Act & Assert
        assertThatThrownBy(() -> userRoleService.revokeRoleFromUser("bob", "CANDIDATE"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── HR_MANAGER scope restrictions ────────────────────────────────────────

    @Test
    void grantRoleToUser_hrManagerGrantsNonHrRole_throwsAccessDenied() {
        // Arrange
        User hrManager = new User();
        hrManager.setId("hrm-id");
        hrManager.setUsername("hrm");
        hrManager.setRoles(new HashSet<>(Set.of("HR_MANAGER")));
        hrManager.setCompanyNo("COMPANY-A");

        setHrManagerContext("hrm");
        Role adminRole = new Role();
        adminRole.setName("ADMIN");
        when(users.findByUsername("hrm")).thenReturn(Optional.of(hrManager));
        when(users.findByUsername("bob")).thenReturn(Optional.of(targetUser));
        when(roles.findByName("ADMIN")).thenReturn(Optional.of(adminRole));

        // Act & Assert — HR_MANAGER cannot grant ADMIN
        assertThatThrownBy(() -> userRoleService.grantRoleToUser("bob", "ADMIN"))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ─── getUserRoles ─────────────────────────────────────────────────────────

    @Test
    void getUserRoles_adminChecksAnyUser_returnsRoles() {
        // Arrange
        setAdminContext();
        when(users.findByUsername("bob")).thenReturn(Optional.of(targetUser));

        // Act
        Set<String> result = userRoleService.getUserRoles("bob");

        // Assert
        org.assertj.core.api.Assertions.assertThat(result).contains("CANDIDATE");
    }

    @Test
    void getUserRoles_userNotFound_throwsNoSuchElement() {
        setAdminContext();
        when(users.findByUsername(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userRoleService.getUserRoles("unknown"))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    // --- grantRolesToUser (batch) -----------------------------------------------

    @Test
    void grantRolesToUser_adminGrantsBatch_succeeds() {
        setAdminContext();
        Role hrRole = new Role();
        hrRole.setName("HR");
        when(users.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(users.findByUsername("bob")).thenReturn(Optional.of(targetUser));
        when(roles.findByName("HR")).thenReturn(Optional.of(hrRole));
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userRoleService.grantRolesToUser("bob", List.of("HR"));

        verify(users).save(targetUser);
        verify(auditService).logGrant("admin", "bob", "ROLE", "HR");
    }

    @Test
    void grantRolesToUser_selfGrant_throwsAccessDenied() {
        setAdminContext();
        assertThatThrownBy(() -> userRoleService.grantRolesToUser("admin", List.of("HR")))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void grantRolesToUser_roleNotFound_throwsNoSuchElement() {
        setAdminContext();
        when(users.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(users.findByUsername("bob")).thenReturn(Optional.of(targetUser));
        when(roles.findByName("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userRoleService.grantRolesToUser("bob", List.of("UNKNOWN")))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void grantRolesToUser_hrManagerGrantsHR_propagatesCompanyInfo() {
        User hrManager = new User();
        hrManager.setId("hrm-id");
        hrManager.setUsername("hrm");
        hrManager.setRoles(new HashSet<>(Set.of("HR_MANAGER")));
        hrManager.setCompanyNo("TAX-001");
        hrManager.setCompanyId("company-id-1");

        User newHR = new User();
        newHR.setId("newhr-id");
        newHR.setUsername("newhr");
        newHR.setRoles(new HashSet<>(Set.of("CANDIDATE")));

        setHrManagerContext("hrm");
        Role hrRole = new Role();
        hrRole.setName("HR");
        when(users.findByUsername("hrm")).thenReturn(Optional.of(hrManager));
        when(users.findByUsername("newhr")).thenReturn(Optional.of(newHR));
        when(roles.findByName("HR")).thenReturn(Optional.of(hrRole));
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userRoleService.grantRolesToUser("newhr", List.of("HR"));

        org.assertj.core.api.Assertions.assertThat(newHR.getCompanyNo()).isEqualTo("TAX-001");
        verify(users).save(newHR);
    }

    @Test
    void grantRolesToUser_hrManagerNoCompany_throwsAccessDenied() {
        User hrManager = new User();
        hrManager.setId("hrm-id");
        hrManager.setUsername("hrm");
        hrManager.setRoles(new HashSet<>(Set.of("HR_MANAGER")));
        hrManager.setCompanyNo(null);

        setHrManagerContext("hrm");
        Role hrRole = new Role();
        hrRole.setName("HR");
        when(users.findByUsername("hrm")).thenReturn(Optional.of(hrManager));
        when(users.findByUsername("bob")).thenReturn(Optional.of(targetUser));
        when(roles.findByName("HR")).thenReturn(Optional.of(hrRole));

        assertThatThrownBy(() -> userRoleService.grantRolesToUser("bob", List.of("HR")))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    // --- revokeRolesFromUser (batch) --------------------------------------------

    @Test
    void revokeRolesFromUser_adminRevokesBatch_succeeds() {
        setAdminContext();
        targetUser.getRoles().add("HR");
        Role hrRole = new Role();
        hrRole.setName("HR");
        when(users.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(users.findByUsername("bob")).thenReturn(Optional.of(targetUser));
        when(roles.findByName("HR")).thenReturn(Optional.of(hrRole));
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userRoleService.revokeRolesFromUser("bob", List.of("HR"));

        verify(users).save(targetUser);
        verify(auditService).logRevoke("admin", "bob", "ROLE", "HR");
    }

    @Test
    void revokeRolesFromUser_removingLastBuiltInRole_throwsIllegalArgument() {
        setAdminContext();
        Role candidateRole = new Role();
        candidateRole.setName("CANDIDATE");
        when(users.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(users.findByUsername("bob")).thenReturn(Optional.of(targetUser));
        when(roles.findByName("CANDIDATE")).thenReturn(Optional.of(candidateRole));

        assertThatThrownBy(() -> userRoleService.revokeRolesFromUser("bob", List.of("CANDIDATE")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void revokeRolesFromUser_selfRevoke_throwsAccessDenied() {
        setAdminContext();
        assertThatThrownBy(() -> userRoleService.revokeRolesFromUser("admin", List.of("ADMIN")))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    // --- getUserRoles: HR_MANAGER scope -----------------------------------------

    @Test
    void getUserRoles_hrManagerViewsHrUser_sameCompany_succeeds() {
        User hrManager = new User();
        hrManager.setId("hrm-id");
        hrManager.setUsername("hrm");
        hrManager.setRoles(new HashSet<>(Set.of("HR_MANAGER")));
        hrManager.setCompanyNo("COMPANY-A");

        User hrUser = new User();
        hrUser.setId("hr-id");
        hrUser.setUsername("hruser");
        hrUser.setRoles(new HashSet<>(Set.of("HR")));
        hrUser.setCompanyNo("COMPANY-A");

        setHrManagerContext("hrm");
        when(users.findByUsername("hruser")).thenReturn(Optional.of(hrUser));
        when(users.findByUsername("hrm")).thenReturn(Optional.of(hrManager));

        Set<String> result = userRoleService.getUserRoles("hruser");

        org.assertj.core.api.Assertions.assertThat(result).contains("HR");
    }

    @Test
    void getUserRoles_hrManagerViewsNonHrUser_throwsAccessDenied() {
        User hrManager = new User();
        hrManager.setId("hrm-id");
        hrManager.setUsername("hrm");
        hrManager.setRoles(new HashSet<>(Set.of("HR_MANAGER")));
        hrManager.setCompanyNo("COMPANY-A");

        setHrManagerContext("hrm");
        when(users.findByUsername("bob")).thenReturn(Optional.of(targetUser));
        when(users.findByUsername("hrm")).thenReturn(Optional.of(hrManager));

        assertThatThrownBy(() -> userRoleService.getUserRoles("bob"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void getUserRoles_hrManagerViewsHrUser_differentCompany_throwsAccessDenied() {
        User hrManager = new User();
        hrManager.setId("hrm-id");
        hrManager.setUsername("hrm");
        hrManager.setRoles(new HashSet<>(Set.of("HR_MANAGER")));
        hrManager.setCompanyNo("COMPANY-A");

        User hrUser = new User();
        hrUser.setId("hr-id");
        hrUser.setUsername("hruser");
        hrUser.setRoles(new HashSet<>(Set.of("HR")));
        hrUser.setCompanyNo("COMPANY-B");

        setHrManagerContext("hrm");
        when(users.findByUsername("hruser")).thenReturn(Optional.of(hrUser));
        when(users.findByUsername("hrm")).thenReturn(Optional.of(hrManager));

        assertThatThrownBy(() -> userRoleService.getUserRoles("hruser"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void getUserRoles_hrManagerViewsOwnRoles_succeeds() {
        User hrManager = new User();
        hrManager.setId("hrm-id");
        hrManager.setUsername("hrm");
        hrManager.setRoles(new HashSet<>(Set.of("HR_MANAGER")));
        hrManager.setCompanyNo("COMPANY-A");

        setHrManagerContext("hrm");
        when(users.findByUsername("hrm")).thenReturn(Optional.of(hrManager));

        Set<String> result = userRoleService.getUserRoles("hrm");

        org.assertj.core.api.Assertions.assertThat(result).contains("HR_MANAGER");
    }

    // --- revokeRoleFromUser: HR_MANAGER scope ------------------------------------

    @Test
    void revokeRoleFromUser_hrManagerRevokesHR_sameCompany_succeeds() {
        User hrManager = new User();
        hrManager.setId("hrm-id");
        hrManager.setUsername("hrm");
        hrManager.setRoles(new HashSet<>(Set.of("HR_MANAGER")));
        hrManager.setCompanyNo("COMPANY-A");

        User hrUser = new User();
        hrUser.setId("hr-id");
        hrUser.setUsername("hruser");
        hrUser.setRoles(new HashSet<>(Set.of("CANDIDATE", "HR")));
        hrUser.setCompanyNo("COMPANY-A");

        setHrManagerContext("hrm");
        Role hrRole = new Role();
        hrRole.setName("HR");
        when(users.findByUsername("hrm")).thenReturn(Optional.of(hrManager));
        when(users.findByUsername("hruser")).thenReturn(Optional.of(hrUser));
        when(roles.findByName("HR")).thenReturn(Optional.of(hrRole));
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userRoleService.revokeRoleFromUser("hruser", "HR");

        verify(users).save(hrUser);
        verify(auditService).logRevoke("hrm", "hruser", "ROLE", "HR");
    }
}
