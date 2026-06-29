package org.workfitai.authservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.workfitai.authservice.config.SecurityTestConfig;
import org.workfitai.authservice.mapper.PermissionMapper;
import org.workfitai.authservice.mapper.RoleMapper;
import org.workfitai.authservice.model.Permission;
import org.workfitai.authservice.model.Role;
import org.workfitai.authservice.security.JwtAuthenticationFilter;
import org.workfitai.authservice.security.SessionValidationFilter;
import org.workfitai.authservice.service.iPermissionService;
import org.workfitai.authservice.service.iRoleService;

@WebMvcTest(AdminController.class)
@Import(SecurityTestConfig.class)
class AdminControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean iPermissionService permissionService;
    @MockBean iRoleService roleService;
    @MockBean PermissionMapper permissionMapper;
    @MockBean RoleMapper roleMapper;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean SessionValidationFilter sessionValidationFilter;

    // ─── Permissions ──────────────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = {"ADMIN", "perm:read"})
    void listPermissions_withAdminAndPermRead_returns200() throws Exception {
        // Arrange
        when(permissionService.listAll()).thenReturn(List.of());
        when(permissionMapper.toResponseList(any())).thenReturn(List.of());

        // Act & Assert
        mockMvc.perform(get("/permissions"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"ADMIN", "perm:create"})
    void createPermission_withAdminAndPermCreate_returns201() throws Exception {
        // Arrange
        Permission perm = new Permission();
        perm.setName("test:read");
        when(permissionService.create(any())).thenReturn(perm);
        when(permissionMapper.toEntity(any())).thenReturn(perm);
        when(permissionMapper.toResponse(any())).thenReturn(null);

        String body = """
                {"name": "test:read", "description": "Test read permission"}
                """;

        // Act & Assert
        mockMvc.perform(post("/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = {"ADMIN", "perm:delete"})
    void deletePermission_withAdminAndPermDelete_returns200() throws Exception {
        // Arrange
        doNothing().when(permissionService).deleteByName(anyString());

        // Act & Assert
        mockMvc.perform(delete("/permissions/test:read"))
                .andExpect(status().isOk());
    }

    // ─── Roles ────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = {"ADMIN", "role:read"})
    void listRoles_withAdminAndRoleRead_returns200() throws Exception {
        // Arrange
        when(roleService.listAll()).thenReturn(List.of());
        when(roleMapper.toResponseList(any())).thenReturn(List.of());

        // Act & Assert
        mockMvc.perform(get("/roles"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"ADMIN", "role:create"})
    void createRole_withAdminAndRoleCreate_returns201() throws Exception {
        // Arrange
        Role role = new Role();
        role.setName("CUSTOM_ROLE");
        when(roleService.create(any())).thenReturn(role);
        when(roleMapper.toEntity(any())).thenReturn(role);
        when(roleMapper.toResponse(any())).thenReturn(null);

        String body = """
                {"name": "CUSTOM_ROLE", "description": "A custom role"}
                """;

        // Act & Assert
        mockMvc.perform(post("/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = {"ADMIN", "role:delete"})
    void deleteRole_withAdminAndRoleDelete_returns200() throws Exception {
        // Arrange
        doNothing().when(roleService).deleteByName(anyString());

        // Act & Assert
        mockMvc.perform(delete("/roles/CUSTOM_ROLE"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"ADMIN", "role:update"})
    void addPermissionToRole_returns200() throws Exception {
        // Arrange
        Role role = new Role();
        role.setName("CUSTOM_ROLE");
        when(roleService.addPermission(anyString(), anyString())).thenReturn(role);
        when(roleMapper.toResponse(any())).thenReturn(null);

        String body = """
                {"permission": "test:read"}
                """;

        // Act & Assert
        mockMvc.perform(post("/roles/CUSTOM_ROLE/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void listPermissions_withoutPermRead_returns403() throws Exception {
        mockMvc.perform(get("/permissions"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listPermissions_withoutAuthentication_returns403() throws Exception {
        mockMvc.perform(get("/permissions"))
                .andExpect(status().isForbidden());
    }

    // ─── GET /permissions/{name} ───────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = {"ADMIN", "perm:read"})
    void getPerm_withAdminAndPermRead_returns200() throws Exception {
        // Arrange
        Permission perm = new Permission();
        perm.setName("test:read");
        when(permissionService.getByName(anyString())).thenReturn(perm);
        when(permissionMapper.toResponse(any())).thenReturn(null);

        // Act & Assert
        mockMvc.perform(get("/permissions/test:read"))
                .andExpect(status().isOk());
    }

    // ─── POST /permissions/batch ───────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = {"ADMIN", "perm:create"})
    void createPermissionsBatch_withValidBody_returns201() throws Exception {
        // Arrange
        when(permissionService.createBatch(any())).thenReturn(List.of());
        when(permissionMapper.toEntity(any())).thenReturn(new Permission());
        when(permissionMapper.toResponseList(any())).thenReturn(List.of());

        String body = """
                {"permissions": [{"name": "test:read", "description": "desc"}]}
                """;

        // Act & Assert
        mockMvc.perform(post("/permissions/batch")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    // ─── PUT /permissions/{name} ───────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = {"ADMIN", "perm:update"})
    void updatePermission_withAdminAndPermUpdate_returns200() throws Exception {
        // Arrange
        Permission perm = new Permission();
        perm.setName("test:read");
        when(permissionService.updateDescription(anyString(), anyString())).thenReturn(perm);
        when(permissionMapper.toResponse(any())).thenReturn(null);

        String body = """
                {"description": "Updated description"}
                """;

        // Act & Assert
        mockMvc.perform(put("/permissions/test:read")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    // ─── GET /roles/{name} ────────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = {"ADMIN", "role:read"})
    void getRole_withAdminAndRoleRead_returns200() throws Exception {
        // Arrange
        Role role = new Role();
        role.setName("HR");
        when(roleService.getByName(anyString())).thenReturn(role);
        when(roleMapper.toResponse(any())).thenReturn(null);

        // Act & Assert
        mockMvc.perform(get("/roles/HR"))
                .andExpect(status().isOk());
    }

    // ─── POST /roles/batch ────────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = {"ADMIN", "role:create"})
    void createRolesBatch_withValidBody_returns201() throws Exception {
        // Arrange
        when(roleService.createBatch(any())).thenReturn(List.of());
        when(roleMapper.toEntity(any())).thenReturn(new Role());
        when(roleMapper.toResponseList(any())).thenReturn(List.of());

        String body = """
                {"roles": [{"name": "CUSTOM_ROLE", "description": "desc"}]}
                """;

        // Act & Assert
        mockMvc.perform(post("/roles/batch")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    // ─── PUT /roles/{name} ────────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = {"ADMIN", "role:update"})
    void updateRole_withAdminAndRoleUpdate_returns200() throws Exception {
        // Arrange
        Role role = new Role();
        role.setName("HR");
        when(roleService.updateDescription(anyString(), anyString())).thenReturn(role);
        when(roleMapper.toResponse(any())).thenReturn(null);

        String body = """
                {"description": "Updated role description"}
                """;

        // Act & Assert
        mockMvc.perform(put("/roles/HR")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    // ─── POST /roles/{roleName}/clone ─────────────────────────────────────────

    @Test
    @WithMockUser(authorities = {"ADMIN", "role:create"})
    void cloneRole_withValidBody_returns201() throws Exception {
        // Arrange
        Role cloned = new Role();
        cloned.setName("CLONED_ROLE");
        when(roleService.cloneRole(anyString(), anyString(), anyString())).thenReturn(cloned);
        when(roleMapper.toResponse(any())).thenReturn(null);

        String body = """
                {"name": "CLONED_ROLE", "description": "Cloned from HR"}
                """;

        // Act & Assert
        mockMvc.perform(post("/roles/HR/clone")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    // ─── POST /roles/{roleName}/permissions/batch ─────────────────────────────

    @Test
    @WithMockUser(authorities = {"ADMIN", "role:update"})
    void addPermissionsToRole_withValidBody_returns200() throws Exception {
        // Arrange
        Role role = new Role();
        role.setName("HR");
        when(roleService.addPermissions(anyString(), anyList())).thenReturn(role);
        when(roleMapper.toResponse(any())).thenReturn(null);

        String body = """
                {"permissions": ["test:read", "test:write"]}
                """;

        // Act & Assert
        mockMvc.perform(post("/roles/HR/permissions/batch")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    // ─── DELETE /roles/{roleName}/permissions?permission= ─────────────────────

    @Test
    @WithMockUser(authorities = {"ADMIN", "role:update"})
    void removePermissionFromRole_withValidParam_returns200() throws Exception {
        // Arrange
        Role role = new Role();
        role.setName("HR");
        when(roleService.removePermission(anyString(), anyString())).thenReturn(role);
        when(roleMapper.toResponse(any())).thenReturn(null);

        // Act & Assert
        mockMvc.perform(delete("/roles/HR/permissions")
                        .param("permission", "test:read"))
                .andExpect(status().isOk());
    }

    // ─── DELETE /roles/{roleName}/permissions/batch ───────────────────────────

    @Test
    @WithMockUser(authorities = {"ADMIN", "role:update"})
    void removePermissionsFromRole_withValidBody_returns200() throws Exception {
        // Arrange
        Role role = new Role();
        role.setName("HR");
        when(roleService.removePermissions(anyString(), anyList())).thenReturn(role);
        when(roleMapper.toResponse(any())).thenReturn(null);

        String body = """
                {"permissions": ["test:read", "test:write"]}
                """;

        // Act & Assert
        mockMvc.perform(delete("/roles/HR/permissions/batch")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }
}
