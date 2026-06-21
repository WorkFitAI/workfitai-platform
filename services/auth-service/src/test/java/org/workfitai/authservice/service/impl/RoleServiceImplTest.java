package org.workfitai.authservice.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import org.workfitai.authservice.model.Role;
import org.workfitai.authservice.repository.PermissionRepository;
import org.workfitai.authservice.repository.RoleRepository;
import org.workfitai.authservice.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class RoleServiceImplTest {

    @Mock
    private RoleRepository roles;

    @Mock
    private PermissionRepository perms;

    @Mock
    private UserRepository users;

    @InjectMocks
    private RoleServiceImpl roleService;

    @Test
    void deleteByName_throwsConflict_whenRoleStillAssignedToUsers() {
        Role role = Role.builder().id("1").name("HR_HRM").build();
        when(roles.findByName("HR_HRM")).thenReturn(Optional.of(role));
        when(users.existsByRolesContains("HR_HRM")).thenReturn(true);

        assertThatThrownBy(() -> roleService.deleteByName("HR_HRM"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Role is assigned to one or more users");

        verify(roles, never()).delete(any());
    }

    @Test
    void deleteByName_deletesRole_whenNoUserHoldsIt() {
        Role role = Role.builder().id("1").name("HR_HRM").build();
        when(roles.findByName("HR_HRM")).thenReturn(Optional.of(role));
        when(users.existsByRolesContains("HR_HRM")).thenReturn(false);

        roleService.deleteByName("HR_HRM");

        verify(roles).delete(eq(role));
    }

    @Test
    void deleteByName_rejectsBuiltInRole_beforeCheckingUsers() {
        assertThatThrownBy(() -> roleService.deleteByName("ADMIN"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(users, never()).existsByRolesContains(any());
        verify(roles, never()).delete(any());
    }
}
