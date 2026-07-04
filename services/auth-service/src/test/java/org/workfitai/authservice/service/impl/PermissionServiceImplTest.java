package org.workfitai.authservice.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import org.workfitai.authservice.model.Permission;
import org.workfitai.authservice.repository.PermissionRepository;
import org.workfitai.authservice.repository.RoleRepository;

@ExtendWith(MockitoExtension.class)
class PermissionServiceImplTest {

    @Mock PermissionRepository perms;
    @Mock RoleRepository roles;
    @InjectMocks PermissionServiceImpl service;

    private Permission perm(String name) {
        return Permission.builder().id("id-1").name(name).description("desc").build();
    }

    // ─── create ───────────────────────────────────────────────────────────────

    @Test
    void create_newPermission_savesAndReturns() {
        Permission p = perm("USER:read");
        when(perms.findByName("USER:read")).thenReturn(Optional.empty());
        when(perms.save(p)).thenReturn(p);

        Permission result = service.create(p);

        assertThat(result.getName()).isEqualTo("USER:read");
        verify(perms).save(p);
    }

    @Test
    void create_duplicateName_throwsIllegalArgument() {
        Permission p = perm("USER:read");
        when(perms.findByName("USER:read")).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.create(p))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("USER:read");
        verify(perms, never()).save(any());
    }

    // ─── listAll ──────────────────────────────────────────────────────────────

    @Test
    void listAll_returnsAllPermissions() {
        List<Permission> all = List.of(perm("USER:read"), perm("USER:write"));
        when(perms.findAll()).thenReturn(all);

        List<Permission> result = service.listAll();

        assertThat(result).hasSize(2);
    }

    // ─── getByName ────────────────────────────────────────────────────────────

    @Test
    void getByName_found_returnsPermission() {
        when(perms.findByName("USER:read")).thenReturn(Optional.of(perm("USER:read")));

        Permission result = service.getByName("USER:read");

        assertThat(result.getName()).isEqualTo("USER:read");
    }

    @Test
    void getByName_notFound_throwsNoSuchElement() {
        when(perms.findByName("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByName("MISSING"))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ─── updateDescription ────────────────────────────────────────────────────

    @Test
    void updateDescription_found_updatesAndSaves() {
        Permission p = perm("USER:read");
        when(perms.findByName("USER:read")).thenReturn(Optional.of(p));
        when(perms.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Permission result = service.updateDescription("USER:read", "New desc");

        assertThat(result.getDescription()).isEqualTo("New desc");
        verify(perms).save(p);
    }

    @Test
    void updateDescription_notFound_throwsNoSuchElement() {
        when(perms.findByName("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateDescription("MISSING", "desc"))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ─── deleteByName ─────────────────────────────────────────────────────────

    @Test
    void deleteByName_notUsedByAnyRole_deletes() {
        Permission p = perm("USER:read");
        when(roles.existsByPermissionsContains("USER:read")).thenReturn(false);
        when(perms.findByName("USER:read")).thenReturn(Optional.of(p));

        service.deleteByName("USER:read");

        verify(perms).delete(p);
    }

    @Test
    void deleteByName_usedByRole_throwsConflict() {
        when(roles.existsByPermissionsContains("USER:read")).thenReturn(true);

        assertThatThrownBy(() -> service.deleteByName("USER:read"))
                .isInstanceOf(ResponseStatusException.class);
        verify(perms, never()).delete(any());
    }

    @Test
    void deleteByName_notFound_throwsNoSuchElement() {
        when(roles.existsByPermissionsContains("MISSING")).thenReturn(false);
        when(perms.findByName("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteByName("MISSING"))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ─── createBatch ─────────────────────────────────────────────────────────

    @Test
    void createBatch_allNew_returnsAllCreated() {
        Permission p1 = perm("USER:read");
        Permission p2 = Permission.builder().name("USER:write").description("d").build();
        when(perms.findByName("USER:read")).thenReturn(Optional.empty());
        when(perms.findByName("USER:write")).thenReturn(Optional.empty());
        when(perms.save(p1)).thenReturn(p1);
        when(perms.save(p2)).thenReturn(p2);

        List<Permission> result = service.createBatch(List.of(p1, p2));

        assertThat(result).hasSize(2);
    }

    @Test
    void createBatch_withDuplicate_throwsAndDoesNotSaveRest() {
        Permission p1 = perm("USER:read");
        when(perms.findByName("USER:read")).thenReturn(Optional.of(p1));

        assertThatThrownBy(() -> service.createBatch(List.of(p1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
