package org.workfitai.authservice.controller;

import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.workfitai.authservice.constants.Messages;
import org.workfitai.authservice.dto.request.BatchCreatePermissionsRequest;
import org.workfitai.authservice.dto.request.BatchCreateRolesRequest;
import org.workfitai.authservice.dto.request.BatchRolePermissionsRequest;
import org.workfitai.authservice.dto.request.RolePermissionRequest;
import org.workfitai.authservice.dto.request.UpdatePermissionRequest;
import org.workfitai.authservice.dto.request.UpdateRoleRequest;
import org.workfitai.authservice.dto.response.CreatePermissionDto;
import org.workfitai.authservice.dto.response.CreateRoleDto;
import org.workfitai.authservice.dto.response.PermissionResponse;
import org.workfitai.authservice.dto.response.ResponseData;
import org.workfitai.authservice.dto.response.RoleResponse;
import org.workfitai.authservice.mapper.PermissionMapper;
import org.workfitai.authservice.mapper.RoleMapper;
import org.workfitai.authservice.service.iPermissionService;
import org.workfitai.authservice.service.iRoleService;
import org.workfitai.authservice.service.iUserRoleService;

@RestController
@PreAuthorize("hasAuthority('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final iPermissionService permissionService;
    private final iRoleService roleService;
    private final iUserRoleService userRoleService;
    private final PermissionMapper permissionMapper;
    private final RoleMapper roleMapper;

    // ==================== PERMISSION ENDPOINTS ====================

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('perm:read')")
    public ResponseEntity<ResponseData<List<PermissionResponse>>> listPerms() {
        var permissions = permissionMapper.toResponseList(permissionService.listAll());
        return ResponseEntity.ok(ResponseData.success(Messages.Success.PERMISSIONS_FETCHED, permissions));
    }

    @GetMapping("/permissions/{name}")
    @PreAuthorize("hasAuthority('perm:read')")
    public ResponseEntity<ResponseData<PermissionResponse>> getPerm(@PathVariable String name) {
        var permission = permissionService.getByName(name);
        return ResponseEntity
                .ok(ResponseData.success(Messages.Success.OPERATION_SUCCESS, permissionMapper.toResponse(permission)));
    }

    @PostMapping("/permissions")
    @PreAuthorize("hasAuthority('perm:create')")
    public ResponseEntity<ResponseData<PermissionResponse>> createPermission(
            @Valid @RequestBody CreatePermissionDto dto) {
        var created = permissionService.create(permissionMapper.toEntity(dto));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseData.success(Messages.Success.PERMISSION_CREATED, permissionMapper.toResponse(created)));
    }

    @PostMapping("/permissions/batch")
    @PreAuthorize("hasAuthority('perm:create')")
    public ResponseEntity<ResponseData<List<PermissionResponse>>> createPermissionsBatch(
            @Valid @RequestBody BatchCreatePermissionsRequest request) {
        var entities = request.getPermissions().stream()
                .map(permissionMapper::toEntity)
                .toList();
        var created = permissionService.createBatch(entities);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseData.success(Messages.Success.PERMISSIONS_CREATED_BATCH,
                        permissionMapper.toResponseList(created)));
    }

    @PutMapping("/permissions/{name}")
    @PreAuthorize("hasAuthority('perm:update')")
    public ResponseEntity<ResponseData<PermissionResponse>> updatePermission(
            @PathVariable String name,
            @Valid @RequestBody UpdatePermissionRequest request) {
        var updated = permissionService.updateDescription(name, request.getDescription());
        return ResponseEntity.ok(ResponseData.success(Messages.Success.PERMISSION_UPDATED,
                permissionMapper.toResponse(updated)));
    }

    @DeleteMapping("/permissions/{name}")
    @PreAuthorize("hasAuthority('perm:delete')")
    public ResponseEntity<ResponseData<Void>> deletePermission(@PathVariable String name) {
        permissionService.deleteByName(name);
        return ResponseEntity.ok(ResponseData.success(Messages.Success.PERMISSION_DELETED));
    }

    // ==================== ROLE ENDPOINTS ====================

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('role:read')")
    public ResponseEntity<ResponseData<List<RoleResponse>>> listRoles() {
        var roles = roleMapper.toResponseList(roleService.listAll());
        return ResponseEntity.ok(ResponseData.success(Messages.Success.ROLES_FETCHED, roles));
    }

    @GetMapping("/roles/{name}")
    @PreAuthorize("hasAuthority('role:read')")
    public ResponseEntity<ResponseData<RoleResponse>> getRole(@PathVariable String name) {
        var role = roleService.getByName(name);
        return ResponseEntity.ok(ResponseData.success(Messages.Success.OPERATION_SUCCESS,
                roleMapper.toResponse(role)));
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('role:create')")
    public ResponseEntity<ResponseData<RoleResponse>> createRole(
            @Valid @RequestBody CreateRoleDto dto) {
        var created = roleService.create(roleMapper.toEntity(dto));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseData.success(Messages.Success.ROLE_CREATED, roleMapper.toResponse(created)));
    }

    @PostMapping("/roles/batch")
    @PreAuthorize("hasAuthority('role:create')")
    public ResponseEntity<ResponseData<List<RoleResponse>>> createRolesBatch(
            @Valid @RequestBody BatchCreateRolesRequest request) {
        var entities = request.getRoles().stream()
                .map(roleMapper::toEntity)
                .toList();
        var created = roleService.createBatch(entities);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseData.success(Messages.Success.ROLES_CREATED_BATCH,
                        roleMapper.toResponseList(created)));
    }

    @PutMapping("/roles/{name}")
    @PreAuthorize("hasAuthority('role:update')")
    public ResponseEntity<ResponseData<RoleResponse>> updateRole(
            @PathVariable String name,
            @Valid @RequestBody UpdateRoleRequest request) {
        var updated = roleService.updateDescription(name, request.getDescription());
        return ResponseEntity.ok(ResponseData.success(Messages.Success.ROLE_UPDATED,
                roleMapper.toResponse(updated)));
    }

    @DeleteMapping("/roles/{name}")
    @PreAuthorize("hasAuthority('role:delete')")
    public ResponseEntity<ResponseData<Void>> deleteRole(@PathVariable String name) {
        roleService.deleteByName(name);
        return ResponseEntity.ok(ResponseData.success(Messages.Success.ROLE_DELETED));
    }

    // ==================== ROLE-PERMISSION MANAGEMENT ====================

    @PostMapping("/roles/{roleName}/permissions")
    @PreAuthorize("hasAuthority('role:update')")
    public ResponseEntity<ResponseData<RoleResponse>> addPermissionToRole(
            @PathVariable String roleName,
            @Valid @RequestBody RolePermissionRequest request) {
        var updated = roleService.addPermission(roleName, request.getPermission());
        return ResponseEntity
                .ok(ResponseData.success(Messages.Success.ROLE_PERMISSION_ADDED, roleMapper.toResponse(updated)));
    }

    @PostMapping("/roles/{roleName}/permissions/batch")
    @PreAuthorize("hasAuthority('role:update')")
    public ResponseEntity<ResponseData<RoleResponse>> addPermissionsToRole(
            @PathVariable String roleName,
            @Valid @RequestBody BatchRolePermissionsRequest request) {
        var updated = roleService.addPermissions(roleName, request.getPermissions());
        return ResponseEntity
                .ok(ResponseData.success(Messages.Success.ROLE_PERMISSIONS_ADDED_BATCH, roleMapper.toResponse(updated)));
    }

    @DeleteMapping("/roles/{roleName}/permissions")
    @PreAuthorize("hasAuthority('role:update')")
    public ResponseEntity<ResponseData<RoleResponse>> removePermissionFromRole(
            @PathVariable String roleName,
            @RequestParam String permission) {
        var updated = roleService.removePermission(roleName, permission);
        return ResponseEntity
                .ok(ResponseData.success(Messages.Success.ROLE_PERMISSION_REMOVED, roleMapper.toResponse(updated)));
    }

    @DeleteMapping("/roles/{roleName}/permissions/batch")
    @PreAuthorize("hasAuthority('role:update')")
    public ResponseEntity<ResponseData<RoleResponse>> removePermissionsFromRole(
            @PathVariable String roleName,
            @Valid @RequestBody BatchRolePermissionsRequest request) {
        var updated = roleService.removePermissions(roleName, request.getPermissions());
        return ResponseEntity
                .ok(ResponseData.success(Messages.Success.ROLE_PERMISSIONS_REMOVED_BATCH, roleMapper.toResponse(updated)));
    }

    // ==================== USER-ROLE MANAGEMENT ====================

    @PostMapping("/users/{username}/roles")
    @PreAuthorize("hasAuthority('role:grant')")
    public ResponseEntity<ResponseData<Void>> grantRole(
            @PathVariable String username,
            @RequestParam String role) {
        userRoleService.grantRoleToUser(username, role);
        return ResponseEntity.ok(ResponseData.success(Messages.Success.ROLE_GRANTED));
    }

    @DeleteMapping("/users/{username}/roles")
    @PreAuthorize("hasAuthority('role:revoke')")
    public ResponseEntity<ResponseData<Void>> revokeRole(
            @PathVariable String username,
            @RequestParam String role) {
        userRoleService.revokeRoleFromUser(username, role);
        return ResponseEntity.ok(ResponseData.success(Messages.Success.ROLE_REVOKED));
    }

    @GetMapping("/users/{username}/roles")
    @PreAuthorize("hasAuthority('role:read')")
    public ResponseEntity<ResponseData<Set<String>>> listUserRoles(
            @PathVariable String username) {
        Set<String> roles = userRoleService.getUserRoles(username);
        return ResponseEntity.ok(ResponseData.success(Messages.Success.USER_ROLES_FETCHED, roles));
    }
}
