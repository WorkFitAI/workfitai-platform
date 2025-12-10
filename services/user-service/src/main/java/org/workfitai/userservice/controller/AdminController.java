package org.workfitai.userservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.workfitai.userservice.constants.Messages;
import org.workfitai.userservice.dto.request.AdminCreateRequest;
import org.workfitai.userservice.dto.request.AdminUpdateRequest;
import org.workfitai.userservice.dto.response.AdminResponse;
import org.workfitai.userservice.dto.response.ResponseData;
import org.workfitai.userservice.service.AdminService;

import java.util.UUID;

@RestController
@RequestMapping("/admins")
@RequiredArgsConstructor
public class AdminController {

  private final AdminService adminService;

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
}
