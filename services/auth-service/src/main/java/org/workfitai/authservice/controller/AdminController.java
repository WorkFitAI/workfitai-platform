package org.workfitai.authservice.controller;

import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.workfitai.authservice.constants.Messages;
import org.workfitai.authservice.dto.CreatePermissionDto;
import org.workfitai.authservice.dto.CreateRoleDto;
import org.workfitai.authservice.dto.PermissionResponse;
import org.workfitai.authservice.dto.RolePermissionRequest;
import org.workfitai.authservice.dto.RoleResponse;
import org.workfitai.authservice.mapper.PermissionMapper;
import org.workfitai.authservice.mapper.RoleMapper;
import org.workfitai.authservice.response.ResponseData;
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

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('perm:read')")
    public ResponseEntity<ResponseData<List<PermissionResponse>>> listPerms() {
        var permissions = permissionMapper.toResponseList(permissionService.listAll());
        return ResponseEntity.ok(ResponseData.success(Messages.Success.PERMISSIONS_FETCHED, permissions));
    }

    @PostMapping("/permissions")
    @PreAuthorize("hasAuthority('perm:create')")
    public ResponseEntity<ResponseData<PermissionResponse>> createPermission(
            @Valid @RequestBody CreatePermissionDto dto) {
        var created = permissionService.create(permissionMapper.toEntity(dto));
        return ResponseEntity.ok(ResponseData.success(Messages.Success.PERMISSION_CREATED, permissionMapper.toResponse(created)));
    }

    @GetMapping("/permissions/{name}")
    @PreAuthorize("hasAuthority('perm:read')")
    public ResponseEntity<ResponseData<PermissionResponse>> getPerm(@PathVariable String name) {
        var permission = permissionService.getByName(name);
        return ResponseEntity.ok(ResponseData.success(Messages.Success.OPERATION_SUCCESS, permissionMapper.toResponse(permission)));
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('role:create')")
    public ResponseEntity<ResponseData<RoleResponse>> createRole(
            @Valid @RequestBody CreateRoleDto dto) {
        var created = roleService.create(roleMapper.toEntity(dto));
        return ResponseEntity.ok(ResponseData.success(Messages.Success.ROLE_CREATED, roleMapper.toResponse(created)));
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('role:read')")
    public ResponseEntity<ResponseData<List<RoleResponse>>> listRoles() {
        var roles = roleMapper.toResponseList(roleService.listAll());
        return ResponseEntity.ok(ResponseData.success(Messages.Success.ROLES_FETCHED, roles));
    }

    @PostMapping("/roles/{roleName}/permissions")
    @PreAuthorize("hasAuthority('role:update')")
    public ResponseEntity<ResponseData<RoleResponse>> addPermissionToRole(
            @PathVariable String roleName,
            @Valid @RequestBody RolePermissionRequest request) {
        var updated = roleService.addPermission(roleName, request.getPermission());
        return ResponseEntity.ok(ResponseData.success(Messages.Success.ROLE_PERMISSION_ADDED, roleMapper.toResponse(updated)));
    }

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
