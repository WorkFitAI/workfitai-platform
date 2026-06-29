package org.workfitai.authservice.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import org.workfitai.authservice.model.Permission;
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

    // ─── create ───────────────────────────────────────────────────────────────

    @Test
    void create_newRole_noPermissions_saves() {
        Role r = Role.builder().name("CUSTOM_ROLE").build();
        when(roles.findByName("CUSTOM_ROLE")).thenReturn(Optional.empty());
        when(roles.save(any())).thenReturn(r);

        assertThat(roleService.create(r).getName()).isEqualTo("CUSTOM_ROLE");
    }

    @Test
    void create_withValidPermissions_saves() {
        Role r = Role.builder().name("CUSTOM_ROLE").permissions(new HashSet<>(Set.of("USER:read"))).build();
        when(roles.findByName("CUSTOM_ROLE")).thenReturn(Optional.empty());
        when(perms.findByName("USER:read")).thenReturn(Optional.of(Permission.builder().name("USER:read").build()));
        when(roles.save(any())).thenReturn(r);

        assertThat(roleService.create(r)).isNotNull();
    }

    @Test
    void create_duplicateName_throwsIllegalArgument() {
        Role r = Role.builder().name("CUSTOM_ROLE").build();
        when(roles.findByName("CUSTOM_ROLE")).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> roleService.create(r))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CUSTOM_ROLE");
        verify(roles, never()).save(any());
    }

    @Test
    void create_unknownPermission_throwsIllegalArgument() {
        Role r = Role.builder().name("CUSTOM_ROLE").permissions(new HashSet<>(Set.of("BAD:perm"))).build();
        when(roles.findByName("CUSTOM_ROLE")).thenReturn(Optional.empty());
        when(perms.findByName("BAD:perm")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.create(r))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BAD:perm");
    }

    // ─── addPermission ────────────────────────────────────────────────────────

    @Test
    void addPermission_builtInRole_throwsIllegalArgument() {
        assertThatThrownBy(() -> roleService.addPermission("CANDIDATE", "USER:read"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CANDIDATE");
    }

    @Test
    void addPermission_success_addsPermission() {
        Role r = Role.builder().name("CUSTOM_ROLE").permissions(new HashSet<>()).build();
        when(roles.findByName("CUSTOM_ROLE")).thenReturn(Optional.of(r));
        when(perms.findByName("USER:read")).thenReturn(Optional.of(Permission.builder().name("USER:read").build()));
        when(roles.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(roleService.addPermission("CUSTOM_ROLE", "USER:read").getPermissions()).contains("USER:read");
    }

    @Test
    void addPermission_roleNotFound_throwsNoSuchElement() {
        when(roles.findByName("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.addPermission("MISSING", "USER:read"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void addPermission_permNotFound_throwsNoSuchElement() {
        when(roles.findByName("CUSTOM_ROLE")).thenReturn(Optional.of(Role.builder().name("CUSTOM_ROLE").permissions(new HashSet<>()).build()));
        when(perms.findByName("BAD:perm")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.addPermission("CUSTOM_ROLE", "BAD:perm"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void addPermission_alreadyAssigned_throwsIllegalArgument() {
        Role r = Role.builder().name("CUSTOM_ROLE").permissions(new HashSet<>(Set.of("USER:read"))).build();
        when(roles.findByName("CUSTOM_ROLE")).thenReturn(Optional.of(r));
        when(perms.findByName("USER:read")).thenReturn(Optional.of(Permission.builder().name("USER:read").build()));

        assertThatThrownBy(() -> roleService.addPermission("CUSTOM_ROLE", "USER:read"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already assigned");
    }

    @Test
    void addPermission_nullPermissionsSet_initializesAndAdds() {
        Role r = Role.builder().name("CUSTOM_ROLE").permissions(null).build();
        when(roles.findByName("CUSTOM_ROLE")).thenReturn(Optional.of(r));
        when(perms.findByName("USER:read")).thenReturn(Optional.of(Permission.builder().name("USER:read").build()));
        when(roles.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(roleService.addPermission("CUSTOM_ROLE", "USER:read").getPermissions()).contains("USER:read");
    }

    // ─── removePermission ─────────────────────────────────────────────────────

    @Test
    void removePermission_builtInRole_throwsIllegalArgument() {
        assertThatThrownBy(() -> roleService.removePermission("ADMIN", "USER:read"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void removePermission_customRole_removesPermission() {
        Role r = Role.builder().name("CUSTOM_ROLE").permissions(new HashSet<>(Set.of("USER:read"))).build();
        when(roles.findByName("CUSTOM_ROLE")).thenReturn(Optional.of(r));
        when(roles.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(roleService.removePermission("CUSTOM_ROLE", "USER:read").getPermissions()).doesNotContain("USER:read");
    }

    @Test
    void removePermission_nullPermissionsSet_doesNotThrow() {
        Role r = Role.builder().name("CUSTOM_ROLE").permissions(null).build();
        when(roles.findByName("CUSTOM_ROLE")).thenReturn(Optional.of(r));
        when(roles.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(roleService.removePermission("CUSTOM_ROLE", "USER:read")).isNotNull();
    }

    // ─── getPermissions ───────────────────────────────────────────────────────

    @Test
    void getPermissions_roleFound_returnsPermissions() {
        Role r = Role.builder().name("CUSTOM_ROLE").permissions(Set.of("USER:read")).build();
        when(roles.findByName("CUSTOM_ROLE")).thenReturn(Optional.of(r));

        assertThat(roleService.getPermissions("CUSTOM_ROLE")).contains("USER:read");
    }

    @Test
    void getPermissions_roleNotFound_returnsEmptySet() {
        when(roles.findByName("MISSING")).thenReturn(Optional.empty());

        assertThat(roleService.getPermissions("MISSING")).isEmpty();
    }

    // ─── listAll / getByName ──────────────────────────────────────────────────

    @Test
    void listAll_returnsAll() {
        when(roles.findAll()).thenReturn(List.of(Role.builder().name("R1").build(), Role.builder().name("R2").build()));

        assertThat(roleService.listAll()).hasSize(2);
    }

    @Test
    void getByName_found_returnsRole() {
        when(roles.findByName("CUSTOM_ROLE")).thenReturn(Optional.of(Role.builder().name("CUSTOM_ROLE").build()));

        assertThat(roleService.getByName("CUSTOM_ROLE").getName()).isEqualTo("CUSTOM_ROLE");
    }

    @Test
    void getByName_notFound_throwsNoSuchElement() {
        when(roles.findByName("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.getByName("MISSING"))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ─── updateDescription ────────────────────────────────────────────────────

    @Test
    void updateDescription_builtInRole_throwsIllegalArgument() {
        assertThatThrownBy(() -> roleService.updateDescription("HR", "desc"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateDescription_customRole_updatesDescription() {
        Role r = Role.builder().name("CUSTOM_ROLE").description("old").permissions(new HashSet<>()).build();
        when(roles.findByName("CUSTOM_ROLE")).thenReturn(Optional.of(r));
        when(roles.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(roleService.updateDescription("CUSTOM_ROLE", "new desc").getDescription()).isEqualTo("new desc");
    }

    // ─── addPermissions / removePermissions batch ─────────────────────────────

    @Test
    void addPermissions_builtInRole_throwsIllegalArgument() {
        assertThatThrownBy(() -> roleService.addPermissions("ADMIN", List.of("USER:read")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addPermissions_customRole_addsAll() {
        Role r = Role.builder().name("CUSTOM_ROLE").permissions(new HashSet<>()).build();
        when(roles.findByName("CUSTOM_ROLE")).thenReturn(Optional.of(r));
        when(perms.findByName("USER:read")).thenReturn(Optional.of(Permission.builder().name("USER:read").build()));
        when(perms.findByName("USER:write")).thenReturn(Optional.of(Permission.builder().name("USER:write").build()));
        when(roles.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(roleService.addPermissions("CUSTOM_ROLE", List.of("USER:read", "USER:write")).getPermissions())
                .containsAll(Set.of("USER:read", "USER:write"));
    }

    @Test
    void addPermissions_unknownPermission_throwsNoSuchElement() {
        when(roles.findByName("CUSTOM_ROLE")).thenReturn(Optional.of(Role.builder().name("CUSTOM_ROLE").permissions(new HashSet<>()).build()));
        when(perms.findByName("BAD:perm")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.addPermissions("CUSTOM_ROLE", List.of("BAD:perm")))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void removePermissions_builtInRole_throwsIllegalArgument() {
        assertThatThrownBy(() -> roleService.removePermissions("HR_MANAGER", List.of("USER:read")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void removePermissions_customRole_removesAll() {
        Role r = Role.builder().name("CUSTOM_ROLE").permissions(new HashSet<>(Set.of("USER:read", "USER:write"))).build();
        when(roles.findByName("CUSTOM_ROLE")).thenReturn(Optional.of(r));
        when(roles.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Role result = roleService.removePermissions("CUSTOM_ROLE", List.of("USER:read", "USER:write"));

        assertThat(result.getPermissions()).doesNotContainAnyElementsOf(List.of("USER:read", "USER:write"));
    }

    // ─── cloneRole ────────────────────────────────────────────────────────────

    @Test
    void cloneRole_success_deepCopiesPermissions() {
        Role source = Role.builder().name("SOURCE").permissions(new HashSet<>(Set.of("USER:read"))).build();
        when(roles.findByName("SOURCE")).thenReturn(Optional.of(source));
        when(roles.findByName("CLONED")).thenReturn(Optional.empty());
        when(roles.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Role cloned = roleService.cloneRole("SOURCE", "CLONED", "A clone");

        assertThat(cloned.getName()).isEqualTo("CLONED");
        assertThat(cloned.getPermissions()).contains("USER:read");
        assertThat(cloned.getPermissions()).isNotSameAs(source.getPermissions());
    }

    @Test
    void cloneRole_sourceHasNullPermissions_clonesEmpty() {
        Role source = Role.builder().name("SOURCE").permissions(null).build();
        when(roles.findByName("SOURCE")).thenReturn(Optional.of(source));
        when(roles.findByName("CLONED")).thenReturn(Optional.empty());
        when(roles.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(roleService.cloneRole("SOURCE", "CLONED", "A clone").getPermissions()).isEmpty();
    }

    @Test
    void cloneRole_targetNameIsBuiltIn_throwsIllegalArgument() {
        Role source = Role.builder().name("SOURCE").permissions(Set.of()).build();
        when(roles.findByName("SOURCE")).thenReturn(Optional.of(source));

        assertThatThrownBy(() -> roleService.cloneRole("SOURCE", "ADMIN", "clone"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cloneRole_targetNameAlreadyExists_throwsIllegalArgument() {
        Role source = Role.builder().name("SOURCE").permissions(Set.of()).build();
        when(roles.findByName("SOURCE")).thenReturn(Optional.of(source));
        when(roles.findByName("EXISTING")).thenReturn(Optional.of(Role.builder().name("EXISTING").build()));

        assertThatThrownBy(() -> roleService.cloneRole("SOURCE", "EXISTING", "desc"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── createBatch ──────────────────────────────────────────────────────────

    @Test
    void createBatch_allNew_returnsAll() {
        Role r1 = Role.builder().name("R1").build();
        Role r2 = Role.builder().name("R2").build();
        when(roles.findByName("R1")).thenReturn(Optional.empty());
        when(roles.findByName("R2")).thenReturn(Optional.empty());
        when(roles.save(r1)).thenReturn(r1);
        when(roles.save(r2)).thenReturn(r2);

        assertThat(roleService.createBatch(List.of(r1, r2))).hasSize(2);
    }

    @Test
    void createBatch_duplicateInBatch_throwsIllegalArgument() {
        Role r = Role.builder().name("DUP").build();
        when(roles.findByName("DUP")).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> roleService.createBatch(List.of(r)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
