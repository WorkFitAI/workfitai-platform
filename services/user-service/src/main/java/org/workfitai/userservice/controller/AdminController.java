package org.workfitai.userservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.workfitai.userservice.constants.Messages;
import org.workfitai.userservice.dto.request.AdminCreateRequest;
import org.workfitai.userservice.dto.request.AdminUpdateRequest;
import org.workfitai.userservice.dto.response.AdminResponse;
import org.workfitai.userservice.dto.response.ResponseData;
import org.workfitai.userservice.dto.response.UserBaseResponse;
import org.workfitai.userservice.dto.elasticsearch.UserSearchRequest;
import org.workfitai.userservice.dto.elasticsearch.UserSearchResponse;
import org.workfitai.userservice.dto.elasticsearch.ReindexRequest;
import org.workfitai.userservice.dto.elasticsearch.ReindexJobResponse;
import org.workfitai.userservice.service.AdminService;
import org.workfitai.userservice.service.UserService;
import org.workfitai.userservice.service.UserSearchService;
import org.workfitai.userservice.service.UserIndexManagementService;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/admins")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminController {

  private final AdminService adminService;
  private final UserService userService;
  private final UserSearchService userSearchService;
  private final UserIndexManagementService indexManagementService;

  @PostMapping
  public ResponseEntity<ResponseData<AdminResponse>> create(@RequestBody AdminCreateRequest dto) {
    return ResponseEntity.ok(ResponseData.success(Messages.Admin.CREATED, adminService.create(dto)));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ResponseData<AdminResponse>> update(@PathVariable UUID id,
      @RequestBody AdminUpdateRequest dto) {
    return ResponseEntity.ok(ResponseData.success(
        Messages.Admin.UPDATED, adminService.update(id, dto)));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ResponseData<Void>> delete(@PathVariable UUID id) {
    adminService.delete(id);
    return ResponseEntity.ok(ResponseData.success(
        Messages.Admin.DELETED, null));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ResponseData<AdminResponse>> getById(@PathVariable UUID id) {
    return ResponseEntity.ok(ResponseData.success(adminService.getById(id)));
  }

  @GetMapping
  public ResponseEntity<ResponseData<Page<AdminResponse>>> search(
      @RequestParam(required = false) String keyword, Pageable pageable) {
    return ResponseEntity.ok(ResponseData.success(adminService.search(keyword, pageable)));
  }

  /**
   * Get all users across all roles (ADMIN, HR_MANAGER, HR, CANDIDATE)
   * For admin user management dashboard
   */
  @GetMapping("/all-users")
  public ResponseEntity<ResponseData<Page<UserBaseResponse>>> getAllUsers(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String role,
      Pageable pageable) {
    Page<UserBaseResponse> users = userService.searchAllUsers(keyword, role, pageable);
    return ResponseEntity.ok(ResponseData.success(users));
  }

  /**
   * Block or unblock user account
   */
  @PutMapping("/users/{id}/block")
  public ResponseEntity<ResponseData<Void>> blockUser(
      @PathVariable UUID id,
      @RequestParam boolean blocked) {
    userService.setUserBlockStatus(id, blocked);
    String message = blocked ? "User blocked successfully" : "User unblocked successfully";
    return ResponseEntity.ok(ResponseData.success(message, null));
  }

  /**
   * Delete user account (soft delete)
   */
  @DeleteMapping("/users/{id}")
  public ResponseEntity<ResponseData<Void>> deleteUser(@PathVariable UUID id) {
    userService.deleteUser(id);
    return ResponseEntity.ok(ResponseData.success("User deleted successfully", null));
  }

  /**
   * Get user details by ID
   */
  @GetMapping("/users/{id}")
  public ResponseEntity<ResponseData<UserBaseResponse>> getUserById(@PathVariable UUID id) {
    UserBaseResponse user = userService.getByUserId(id);
    return ResponseEntity.ok(ResponseData.success(user));
  }

  /**
   * Get full user profile with role-specific details
   */
  @GetMapping("/users/{id}/full-profile")
  public ResponseEntity<ResponseData<Object>> getFullUserProfile(@PathVariable UUID id) {
    Object profile = userService.getCurrentUserProfile(id);
    return ResponseEntity.ok(ResponseData.success(profile));
  }

  /**
   * Advanced user search using Elasticsearch with filters and aggregations
   */
  @PostMapping("/users/search")
  public ResponseEntity<ResponseData<UserSearchResponse>> searchUsersAdvanced(
      @RequestBody UserSearchRequest searchRequest) {
    UserSearchResponse result = userSearchService.searchUsers(searchRequest);
    return ResponseEntity.ok(ResponseData.success(result));
  }

  /**
   * Trigger bulk reindex of all users from PostgreSQL to Elasticsearch
   * This is an async operation that returns immediately with a job ID
   */
  @PostMapping("/users/reindex")
  public ResponseEntity<ResponseData<String>> triggerReindex(
      @RequestBody(required = false) ReindexRequest reindexRequest) {
    ReindexRequest request = reindexRequest != null ? reindexRequest : new ReindexRequest();
    CompletableFuture<ReindexJobResponse> future = indexManagementService.triggerReindex(request);

    // Get job ID from the future (it's available immediately)
    future.thenAccept(response -> {
      if ("COMPLETED".equals(response.getStatus())) {
        log.info("Reindex completed: {}", response);
      } else {
        log.error("Reindex failed: {}", response.getErrorMessage());
      }
    });

    return ResponseEntity.ok(ResponseData.success(
        "Reindex job started. Check logs for progress.",
        null));
  }
}
